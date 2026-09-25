package com.siparo.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    long countByCustomerIdAndReadAtIsNull(UUID customerId);

    Optional<Notification> findByIdAndCustomerId(UUID id, UUID customerId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.customerId = :customerId and n.readAt is null")
    int markAllRead(@Param("customerId") UUID customerId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from Notification n where n.customerId = :customerId")
    void deleteAllByCustomerId(@Param("customerId") UUID customerId);
}
