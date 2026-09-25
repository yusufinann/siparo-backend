package com.siparo.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coupon_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class CouponRedemption {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID couponId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private BigDecimal discountAmount;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
