package com.siparo.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerRestaurantRepository extends JpaRepository<CustomerRestaurant, CustomerRestaurant.CustomerRestaurantId> {
    List<CustomerRestaurant> findAllById_CustomerIdAndRemovedAtIsNull(UUID customerId);

    List<CustomerRestaurant> findAllById_CustomerId(UUID customerId);

    long countById_RestaurantId(UUID restaurantId);

    long countById_RestaurantIdAndCreatedAtGreaterThanEqual(UUID restaurantId, LocalDateTime from);

    @Query("select cr.source, count(cr) from CustomerRestaurant cr where cr.id.restaurantId = :restaurantId group by cr.source")
    List<Object[]> countBySource(@Param("restaurantId") UUID restaurantId);

    List<CustomerRestaurant> findAllById_RestaurantIdOrderByCreatedAtDesc(UUID restaurantId);
}
