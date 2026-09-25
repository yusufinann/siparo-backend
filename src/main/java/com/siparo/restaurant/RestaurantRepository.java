package com.siparo.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
    Optional<Restaurant> findByPublicCodeIgnoreCase(String publicCode);

    Optional<Restaurant> findFirstByOwnerPhone(String ownerPhone);

    boolean existsByOwnerPhone(String ownerPhone);

    boolean existsByPublicCode(String publicCode);
}
