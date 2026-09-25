package com.siparo.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Teslim edilmiş siparişe müşteri değerlendirmesi. Restoran puanı yalnızca bunlardan hesaplanır. */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private short rating;

    /** Virgülle ayrılmış etiket kodları (TASTY, FAST_DELIVERY, HOT, …). */
    private String tags;

    @Column(length = 1000)
    private String comment;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
