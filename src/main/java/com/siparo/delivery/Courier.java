package com.siparo.delivery;

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
@Table(name = "couriers", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "phone_number"}))
@Getter
@Setter
@NoArgsConstructor
public class Courier {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String status = CourierStatus.OFFLINE.name();

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
