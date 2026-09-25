package com.siparo.restaurant;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.common.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private static final int PUBLIC_CODE_LENGTH = 8;

    private final RestaurantRepository restaurantRepository;
    private final OpeningHourRepository openingHourRepository;
    private final RestaurantMapper mapper;

    @Transactional(readOnly = true)
    public RestaurantDto getRestaurant(UUID id) {
        Restaurant restaurant = findOrThrow(id);
        return mapper.toPublic(restaurant, hoursOf(id));
    }

    @Transactional(readOnly = true)
    public RestaurantDto getByPublicCode(String code) {
        Restaurant restaurant = restaurantRepository.findByPublicCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("RESTAURANT_NOT_FOUND", "Restaurant not found"));
        return mapper.toPublic(restaurant, hoursOf(restaurant.getId()));
    }

    @Transactional(readOnly = true)
    public RestaurantDto getOwnerView(UUID id) {
        Restaurant restaurant = findOrThrow(id);
        return mapper.toOwner(restaurant, hoursOf(id));
    }

    public Restaurant findOrThrow(UUID id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RESTAURANT_NOT_FOUND", "Restaurant not found"));
    }

    public List<OpeningHour> hoursOf(UUID restaurantId) {
        return openingHourRepository.findAllByRestaurantIdOrderByDayOfWeekAsc(restaurantId);
    }

    /** Liste uçları için: çalışma saatlerini tek sorguda yükler. */
    public Map<UUID, List<OpeningHour>> hoursOf(Collection<UUID> restaurantIds) {
        if (restaurantIds.isEmpty()) return Map.of();
        return openingHourRepository.findAllByRestaurantIdIn(restaurantIds).stream()
                .collect(Collectors.groupingBy(OpeningHour::getRestaurantId));
    }

    /** Sipariş almaya uygun mu? Kapalıysa istemcinin anlayacağı kodla reddeder. */
    public RestaurantDto requireOpen(Restaurant restaurant) {
        RestaurantDto view = mapper.toPublic(restaurant, hoursOf(restaurant.getId()));
        if ("TEMPORARILY_CLOSED".equals(view.getDisplayStatus())) {
            throw new BusinessException("RESTAURANT_TEMPORARILY_CLOSED", "Restaurant is not accepting orders");
        }
        if (!view.isOpen()) {
            throw new BusinessException("RESTAURANT_CLOSED", "Restaurant is currently closed",
                    view.getNextOpeningAt() == null ? null : Map.of("nextOpeningAt", view.getNextOpeningAt().toString()));
        }
        return view;
    }

    @Transactional
    public RestaurantDto updateRestaurant(UUID id, RestaurantUpdateRequest request) {
        Restaurant restaurant = findOrThrow(id);

        if (request.deliveryTimeMin() != null && request.deliveryTimeMax() != null
                && request.deliveryTimeMin() > request.deliveryTimeMax()) {
            throw new BusinessException("INVALID_DELIVERY_TIME", "Minimum delivery time cannot exceed maximum");
        }
        boolean deliveryEnabled = request.deliveryEnabled() == null || request.deliveryEnabled();
        boolean pickupEnabled = request.pickupEnabled() != null && request.pickupEnabled();
        if (!deliveryEnabled && !pickupEnabled) {
            throw new BusinessException("FULFILLMENT_REQUIRED", "At least one of delivery or pickup must be enabled");
        }
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new BusinessException("INVALID_LOCATION", "Latitude and longitude must be provided together");
        }

        restaurant.setName(request.name().trim());
        restaurant.setDescription(trimToNull(request.description()));
        restaurant.setLogoUrl(trimToNull(request.logoUrl()));
        restaurant.setCoverUrl(trimToNull(request.coverUrl()));
        restaurant.setPhone(trimToNull(request.phone()));
        restaurant.setAddress(trimToNull(request.address()));
        restaurant.setStatus(request.status());
        restaurant.setMinOrderAmount(request.minOrderAmount());
        restaurant.setDeliveryFee(request.deliveryFee());
        restaurant.setFreeDeliveryThreshold(request.freeDeliveryThreshold() == null ? BigDecimal.ZERO : request.freeDeliveryThreshold());
        restaurant.setDeliveryTimeMin(request.deliveryTimeMin());
        restaurant.setDeliveryTimeMax(request.deliveryTimeMax());
        restaurant.setTags(trimToNull(request.tags()));
        restaurant.setPaymentMethods(trimToNull(request.paymentMethods()));
        restaurant.setLatitude(request.latitude());
        restaurant.setLongitude(request.longitude());
        restaurant.setDeliveryRadiusKm(request.deliveryRadiusKm());
        restaurant.setDeliveryEnabled(deliveryEnabled);
        restaurant.setPickupEnabled(pickupEnabled);
        restaurant.setMarketplaceCommissionRate(request.marketplaceCommissionRate());
        restaurantRepository.save(restaurant);

        if (request.openingHours() != null) {
            replaceOpeningHours(id, request.openingHours());
        }
        return mapper.toOwner(restaurant, hoursOf(id));
    }

    /** Başlıktaki "sipariş al / durdur" anahtarı: diğer ayarlara dokunmadan durum değiştirir. */
    @Transactional
    public RestaurantDto updateStatus(UUID id, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new BusinessException("INVALID_STATUS", "Status must be ACTIVE or INACTIVE");
        }
        Restaurant restaurant = findOrThrow(id);
        restaurant.setStatus(status);
        return mapper.toOwner(restaurantRepository.save(restaurant), hoursOf(id));
    }

    private void replaceOpeningHours(UUID restaurantId, List<RestaurantUpdateRequest.OpeningHourInput> inputs) {
        Set<Integer> days = new HashSet<>();
        for (RestaurantUpdateRequest.OpeningHourInput input : inputs) {
            if (!days.add(input.dayOfWeek())) {
                throw new BusinessException("DUPLICATE_OPENING_DAY", "Each day can only have one opening range");
            }
        }
        openingHourRepository.deleteAllByRestaurantId(restaurantId);
        openingHourRepository.flush();
        List<OpeningHour> hours = inputs.stream().map(input -> {
            OpeningHour hour = new OpeningHour();
            hour.setRestaurantId(restaurantId);
            hour.setDayOfWeek((short) input.dayOfWeek());
            hour.setOpensAt(LocalTime.parse(input.opensAt()));
            hour.setClosesAt(LocalTime.parse(input.closesAt()));
            return hour;
        }).toList();
        openingHourRepository.saveAll(hours);
    }

    public String newPublicCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = CodeGenerator.code(PUBLIC_CODE_LENGTH);
            if (!restaurantRepository.existsByPublicCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate a unique restaurant code");
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
