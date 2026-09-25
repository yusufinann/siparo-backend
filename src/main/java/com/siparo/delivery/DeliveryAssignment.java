package com.siparo.delivery;

import com.siparo.order.Order;
import com.siparo.restaurant.Restaurant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_assignments")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAssignment {
    @Id
    private UUID id = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false)
    private Courier courier;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String deliveryPinHash;

    @Column(nullable = false)
    private String deliveryPinEncrypted;

    @Column(nullable = false)
    private int failedPinAttempts;

    private Instant pinLockedUntil;
    private String failureReason;
    private Instant assignedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;

    private Double lastLatitude;
    private Double lastLongitude;
    private Double lastAccuracy;
    private Double lastHeading;
    private Double lastSpeed;
    private Instant lastLocationAt;

    @Version
    private long version;

    @UpdateTimestamp
    private Instant updatedAt;
}
