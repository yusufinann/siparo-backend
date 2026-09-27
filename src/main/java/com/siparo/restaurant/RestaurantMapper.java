package com.siparo.restaurant;

import com.siparo.common.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Restoranın tek eşleyicisi: açık/kapalı durumu, çalışma saatleri ve teslimat bölgesi her yerde aynı hesaplanır. */
@Component
@RequiredArgsConstructor
public class RestaurantMapper {
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final OpeningHoursService openingHoursService;

    public RestaurantDto toPublic(Restaurant restaurant, List<OpeningHour> hours) {
        RestaurantDto dto = new RestaurantDto();
        dto.setId(restaurant.getId());
        dto.setPublicCode(restaurant.getPublicCode());
        dto.setName(restaurant.getName());
        dto.setDescription(restaurant.getDescription());
        dto.setLogoUrl(restaurant.getLogoUrl());
        dto.setCoverUrl(restaurant.getCoverUrl());
        dto.setPhone(restaurant.getPhone());
        dto.setAddress(restaurant.getAddress());
        dto.setStatus(restaurant.getStatus());
        dto.setMinOrderAmount(orZero(restaurant.getMinOrderAmount()));
        dto.setDeliveryFee(orZero(restaurant.getDeliveryFee()));
        dto.setFreeDeliveryThreshold(orZero(restaurant.getFreeDeliveryThreshold()));
        dto.setDeliveryTimeMin(restaurant.getDeliveryTimeMin());
        dto.setDeliveryTimeMax(restaurant.getDeliveryTimeMax());
        dto.setRatingScore(restaurant.getRatingScore());
        dto.setRatingCount(restaurant.getRatingCount() == null ? 0 : restaurant.getRatingCount());
        dto.setTags(restaurant.getTags());
        dto.setPaymentMethods(restaurant.getPaymentMethods());
        dto.setMealCards(restaurant.getMealCards());
        dto.setLatitude(restaurant.getLatitude());
        dto.setLongitude(restaurant.getLongitude());
        dto.setDeliveryRadiusKm(restaurant.getDeliveryRadiusKm());
        dto.setDeliveryEnabled(restaurant.isDeliveryEnabled());
        dto.setPickupEnabled(restaurant.isPickupEnabled());
        dto.setOpeningHours(hours.stream().map(this::toHourDto).toList());

        OpeningHoursService.OpenState state = openingHoursService.state(hours);
        boolean accepting = "ACTIVE".equals(restaurant.getStatus());
        dto.setOpen(accepting && state.withinHours());
        if (!accepting) {
            dto.setDisplayStatus("TEMPORARILY_CLOSED");
        } else if (!state.withinHours()) {
            dto.setDisplayStatus("CLOSED");
            dto.setNextOpeningAt(state.nextOpeningAt());
        } else {
            dto.setDisplayStatus("OPEN");
        }
        return dto;
    }

    public RestaurantDto toOwner(Restaurant restaurant, List<OpeningHour> hours) {
        RestaurantDto dto = toPublic(restaurant, hours);
        dto.setEmail(restaurant.getEmail());
        dto.setOwnerPhone(restaurant.getOwnerPhone());
        dto.setMarketplaceCommissionRate(restaurant.getMarketplaceCommissionRate());
        return dto;
    }

    /** Müşteri konumuna göre mesafe ve teslimat bölgesi bilgisini ekler (koordinat yoksa hiçbir şey eklenmez). */
    public void applyCustomerLocation(RestaurantDto dto, Restaurant restaurant, Double latitude, Double longitude) {
        Double distance = GeoUtil.distanceKm(restaurant.getLatitude(), restaurant.getLongitude(), latitude, longitude);
        if (distance == null) return;
        dto.setDistanceKm(Math.round(distance * 10.0) / 10.0);
        if (restaurant.getDeliveryRadiusKm() != null) {
            dto.setWithinDeliveryArea(distance <= restaurant.getDeliveryRadiusKm().doubleValue());
        }
    }

    private RestaurantDto.OpeningHourDto toHourDto(OpeningHour hour) {
        RestaurantDto.OpeningHourDto dto = new RestaurantDto.OpeningHourDto();
        dto.setDayOfWeek(hour.getDayOfWeek());
        dto.setOpensAt(hour.getOpensAt().format(HH_MM));
        dto.setClosesAt(hour.getClosesAt().format(HH_MM));
        return dto;
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
