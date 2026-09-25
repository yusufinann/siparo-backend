package com.siparo.coupon;

import com.siparo.restaurant.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** İşletmenin oluşturduğu kampanya kuponu. */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 300)
    private String description;

    /** AMOUNT, PERCENT, FREE_DELIVERY */
    @Column(nullable = false, length = 20)
    private String discountType = "AMOUNT";

    private BigDecimal discountAmount;

    private BigDecimal discountPercent;

    private BigDecimal maxDiscountAmount;

    private BigDecimal minOrderAmount;

    private LocalDate startsOn;

    @Column(nullable = false)
    private LocalDate expiryDate;

    private int perCustomerLimit = 1;

    private Integer totalLimit;

    private boolean firstOrderOnly;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
