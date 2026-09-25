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

/** Müşterinin sipariş sorunu bildirimi; işletme panelinde "operasyonel sorunlar" olarak görünür. */
@Entity
@Table(name = "order_issues")
@Getter
@Setter
@NoArgsConstructor
public class OrderIssue {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private UUID customerId;

    /** MISSING_ITEM, WRONG_ITEM, LATE_DELIVERY, COLD_FOOD, QUALITY, OTHER */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 1000)
    private String detail;

    /** OPEN, RESOLVED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(length = 500)
    private String resolutionNote;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
