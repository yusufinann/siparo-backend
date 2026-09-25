package com.siparo.delivery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier_location_points")
@Getter
@Setter
@NoArgsConstructor
public class CourierLocationPoint {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_assignment_id", nullable = false)
    private DeliveryAssignment deliveryAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false)
    private Courier courier;

    @Column(nullable = false)
    private double latitude;
    @Column(nullable = false)
    private double longitude;
    private Double accuracy;
    private Double heading;
    private Double speed;
    @Column(nullable = false)
    private Instant recordedAt;
    @CreationTimestamp
    private Instant receivedAt;
}
