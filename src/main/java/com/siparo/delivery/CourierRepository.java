package com.siparo.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {
    List<Courier> findAllByRestaurantIdOrderByFullNameAsc(UUID restaurantId);

    Optional<Courier> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Optional<Courier> findByRestaurantIdAndPhoneNumber(UUID restaurantId, String phoneNumber);

    List<Courier> findAllByPhoneNumber(String phoneNumber);
}
