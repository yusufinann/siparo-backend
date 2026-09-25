package com.siparo.coupon;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponRequest(
        @NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "^[A-Za-z0-9-]+$") String code,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 300) String description,
        @NotNull @Pattern(regexp = "AMOUNT|PERCENT|FREE_DELIVERY") String discountType,
        @DecimalMin("0.01") BigDecimal discountAmount,
        @DecimalMin("1") @DecimalMax("100") BigDecimal discountPercent,
        @DecimalMin("0.01") BigDecimal maxDiscountAmount,
        @DecimalMin("0") BigDecimal minOrderAmount,
        LocalDate startsOn,
        @NotNull LocalDate expiryDate,
        @Min(1) @Max(100) Integer perCustomerLimit,
        @Min(1) Integer totalLimit,
        Boolean firstOrderOnly,
        Boolean active) {}
