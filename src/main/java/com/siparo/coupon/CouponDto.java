package com.siparo.coupon;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CouponDto {
    private UUID id;
    private UUID restaurantId;
    private String restaurantName;
    private String restaurantLogoUrl;
    private String code;
    private String title;
    private String description;
    private String discountType;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private LocalDate startsOn;
    private LocalDate expiryDate;
    private Integer perCustomerLimit;
    private Integer totalLimit;
    private Boolean firstOrderOnly;
    private Boolean active;
    /** Müşteri: AVAILABLE, USED, EXPIRED. İşletme: ACTIVE, SCHEDULED, EXPIRED, INACTIVE, EXHAUSTED. */
    private String state;
    private Long usageCount;
    private BigDecimal totalDiscountGiven;
}
