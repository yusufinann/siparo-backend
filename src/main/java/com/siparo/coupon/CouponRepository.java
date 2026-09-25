package com.siparo.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByRestaurantIdAndCodeIgnoreCase(UUID restaurantId, String code);

    boolean existsByRestaurantIdAndCodeIgnoreCase(UUID restaurantId, String code);

    List<Coupon> findAllByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId);

    List<Coupon> findAllByRestaurantIdInAndExpiryDateGreaterThanEqualOrderByExpiryDateAsc(Collection<UUID> restaurantIds, LocalDate from);

    Optional<Coupon> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
