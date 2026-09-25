package com.siparo.feedback;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByOrderId(UUID orderId);

    List<Review> findAllByOrderIdIn(Collection<UUID> orderIds);

    boolean existsByOrderId(UUID orderId);

    Page<Review> findAllByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    @Query("select avg(r.rating), count(r) from Review r where r.restaurantId = :restaurantId")
    List<Object[]> aggregate(@Param("restaurantId") UUID restaurantId);

    @Query("select r.rating, count(r) from Review r where r.restaurantId = :restaurantId group by r.rating")
    List<Object[]> distribution(@Param("restaurantId") UUID restaurantId);
}
