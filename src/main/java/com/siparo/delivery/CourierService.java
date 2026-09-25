package com.siparo.delivery;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.common.util.BusinessClock;
import com.siparo.common.util.PhoneNumbers;
import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierService {
    private static final List<String> ACTIVE_STATUSES = List.of(
            DeliveryAssignmentStatus.ASSIGNED.name(), DeliveryAssignmentStatus.ACCEPTED.name(), DeliveryAssignmentStatus.ON_THE_WAY.name());

    private final CourierRepository courierRepository;
    private final RestaurantRepository restaurantRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CourierDto create(UUID restaurantId, DeliveryRequests.CreateCourier request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("RESTAURANT_NOT_FOUND", "Restaurant not found"));
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        if (courierRepository.findByRestaurantIdAndPhoneNumber(restaurantId, phone).isPresent()) {
            throw new BusinessException("COURIER_PHONE_EXISTS", "Courier phone number is already registered for this restaurant");
        }
        Courier courier = new Courier();
        courier.setRestaurant(restaurant);
        courier.setFullName(request.fullName().trim());
        courier.setPhoneNumber(phone);
        courier.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        return toDto(courierRepository.save(courier));
    }

    @Transactional(readOnly = true)
    public List<CourierDto> list(UUID restaurantId) {
        Instant startOfDay = BusinessClock.today().atStartOfDay(BusinessClock.ZONE).toInstant();
        return courierRepository.findAllByRestaurantIdOrderByFullNameAsc(restaurantId).stream().map(courier -> {
            CourierDto dto = toDto(courier);
            dto.setActiveDeliveries(assignmentRepository.countByCourierIdAndStatusIn(courier.getId(), ACTIVE_STATUSES));
            dto.setCompletedToday(assignmentRepository.countByCourierIdAndStatusAndCompletedAtGreaterThanEqual(
                    courier.getId(), DeliveryAssignmentStatus.DELIVERED.name(), startOfDay));
            return dto;
        }).toList();
    }

    @Transactional(readOnly = true)
    public CourierDto profile(UUID courierId) {
        return toDto(courierRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("COURIER_NOT_FOUND", "Courier not found")));
    }

    @Transactional
    public CourierDto update(UUID restaurantId, UUID courierId, DeliveryRequests.UpdateCourier request) {
        Courier courier = ownedCourier(restaurantId, courierId);
        if (request.fullName() != null && !request.fullName().isBlank()) courier.setFullName(request.fullName().trim());
        if (request.active() != null) {
            if (!request.active() && assignmentRepository.countByCourierIdAndStatusIn(courierId, ACTIVE_STATUSES) > 0) {
                throw new BusinessException("COURIER_HAS_ACTIVE_DELIVERY", "Courier has active deliveries");
            }
            courier.setActive(request.active());
            if (!request.active()) courier.setStatus(CourierStatus.OFFLINE.name());
        }
        return toDto(courier);
    }

    @Transactional
    public void resetPassword(UUID restaurantId, UUID courierId, String password) {
        Courier courier = ownedCourier(restaurantId, courierId);
        courier.setPasswordHash(passwordEncoder.encode(password));
    }

    @Transactional
    public CourierDto updateAvailability(UUID courierId, String value) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("COURIER_NOT_FOUND", "Courier not found"));
        CourierStatus status;
        try {
            status = CourierStatus.valueOf(value);
        } catch (Exception e) {
            throw new BusinessException("INVALID_STATUS", "Unknown courier status");
        }
        if (status == CourierStatus.BUSY) throw new BusinessException("INVALID_STATUS", "Busy status is managed by active deliveries");
        if (!courier.isActive()) throw new BusinessException("COURIER_INACTIVE", "Courier account is inactive");
        if (status == CourierStatus.OFFLINE
                && assignmentRepository.existsByCourierIdAndStatus(courierId, DeliveryAssignmentStatus.ON_THE_WAY.name())) {
            throw new BusinessException("COURIER_HAS_ACTIVE_DELIVERY", "Finish the active delivery before going offline");
        }
        if (CourierStatus.BUSY.name().equals(courier.getStatus()) && status == CourierStatus.AVAILABLE) {
            return toDto(courier); // yoldayken meşgul kalır
        }
        courier.setStatus(status.name());
        return toDto(courier);
    }

    /**
     * Telefon + şifre ile giriş. Telefon birden fazla restoranda kayıtlıysa restoran kodu gerekir (COURIER_RESTAURANT_REQUIRED).
     */
    @Transactional
    public Courier authenticate(DeliveryRequests.CourierLogin request) {
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        UUID restaurantId = request.restaurantId();
        if (restaurantId == null && request.restaurantCode() != null && !request.restaurantCode().isBlank()) {
            restaurantId = restaurantRepository.findByPublicCodeIgnoreCase(request.restaurantCode().trim())
                    .map(Restaurant::getId)
                    .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Invalid courier credentials", HttpStatus.UNAUTHORIZED));
        }
        List<Courier> candidates;
        if (restaurantId != null) {
            candidates = courierRepository.findByRestaurantIdAndPhoneNumber(restaurantId, phone).map(List::of).orElse(List.of());
        } else {
            candidates = courierRepository.findAllByPhoneNumber(phone);
            if (candidates.isEmpty() && !phone.equals(request.phoneNumber().trim())) {
                candidates = courierRepository.findAllByPhoneNumber(request.phoneNumber().trim());
            }
        }
        List<Courier> matching = candidates.stream()
                .filter(courier -> passwordEncoder.matches(request.password(), courier.getPasswordHash()))
                .toList();
        if (matching.isEmpty()) {
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid courier credentials", HttpStatus.UNAUTHORIZED);
        }
        if (matching.size() > 1) {
            throw new BusinessException("COURIER_RESTAURANT_REQUIRED", "Restaurant code is required for this phone number");
        }
        Courier courier = matching.get(0);
        if (!courier.isActive()) {
            throw new BusinessException("COURIER_INACTIVE", "Courier account is inactive", HttpStatus.UNAUTHORIZED);
        }
        if (!CourierStatus.BUSY.name().equals(courier.getStatus())) {
            courier.setStatus(CourierStatus.AVAILABLE.name());
        }
        return courier;
    }

    private Courier ownedCourier(UUID restaurantId, UUID courierId) {
        return courierRepository.findByIdAndRestaurantId(courierId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("COURIER_NOT_FOUND", "Courier not found"));
    }

    public CourierDto toDto(Courier courier) {
        CourierDto dto = new CourierDto();
        dto.setId(courier.getId());
        dto.setRestaurantId(courier.getRestaurant().getId());
        dto.setRestaurantName(courier.getRestaurant().getName());
        dto.setFullName(courier.getFullName());
        dto.setPhoneNumber(courier.getPhoneNumber());
        dto.setStatus(courier.getStatus());
        dto.setActive(courier.isActive());
        return dto;
    }
}
