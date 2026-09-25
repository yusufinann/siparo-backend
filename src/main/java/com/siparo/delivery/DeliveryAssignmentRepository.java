package com.siparo.delivery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {
    Optional<DeliveryAssignment> findByOrderId(UUID orderId);

    List<DeliveryAssignment> findAllByOrderIdIn(Collection<UUID> orderIds);

    Optional<DeliveryAssignment> findByIdAndCourierId(UUID id, UUID courierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from DeliveryAssignment assignment where assignment.id = :assignmentId and assignment.courier.id = :courierId")
    Optional<DeliveryAssignment> findForLocationUpdate(
            @Param("assignmentId") UUID assignmentId, @Param("courierId") UUID courierId);

    List<DeliveryAssignment> findAllByCourierIdOrderByAssignedAtDesc(UUID courierId);

    List<DeliveryAssignment> findAllByRestaurantIdOrderByAssignedAtDesc(UUID restaurantId);

    List<DeliveryAssignment> findAllByRestaurantIdAndStatusIn(UUID restaurantId, Collection<String> statuses);

    boolean existsByCourierIdAndStatus(UUID courierId, String status);

    long countByCourierIdAndStatusAndCompletedAtGreaterThanEqual(UUID courierId, String status, Instant from);

    long countByCourierIdAndStatusIn(UUID courierId, Collection<String> statuses);
}
