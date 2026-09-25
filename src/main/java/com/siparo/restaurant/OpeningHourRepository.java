package com.siparo.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OpeningHourRepository extends JpaRepository<OpeningHour, UUID> {
    List<OpeningHour> findAllByRestaurantIdOrderByDayOfWeekAsc(UUID restaurantId);

    List<OpeningHour> findAllByRestaurantIdIn(Collection<UUID> restaurantIds);

    @Modifying
    @Query("delete from OpeningHour h where h.restaurantId = :restaurantId")
    void deleteAllByRestaurantId(@Param("restaurantId") UUID restaurantId);
}
