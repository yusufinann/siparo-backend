package com.siparo.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderIssueRepository extends JpaRepository<OrderIssue, UUID> {
    List<OrderIssue> findAllByOrderIdIn(Collection<UUID> orderIds);

    Optional<OrderIssue> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    boolean existsByOrderIdAndStatus(UUID orderId, String status);

    List<OrderIssue> findAllByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId);

    List<OrderIssue> findAllByRestaurantIdAndStatusOrderByCreatedAtDesc(UUID restaurantId, String status);

    long countByRestaurantIdAndStatus(UUID restaurantId, String status);

    Optional<OrderIssue> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
