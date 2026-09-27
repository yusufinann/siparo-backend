package com.siparo.restaurant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** İşletme ayarları. Puan alanları yoktur: puan yalnızca müşteri değerlendirmelerinden hesaplanır. */
public record RestaurantUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String coverUrl,
        @Size(max = 50) String phone,
        @Size(max = 1000) String address,
        @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @NotNull @DecimalMin("0") BigDecimal minOrderAmount,
        @NotNull @DecimalMin("0") BigDecimal deliveryFee,
        @DecimalMin("0") BigDecimal freeDeliveryThreshold,
        @Min(1) @Max(240) Integer deliveryTimeMin,
        @Min(1) @Max(240) Integer deliveryTimeMax,
        @Size(max = 255) String tags,
        @Size(max = 255) String paymentMethods,
        @Size(max = 255) String mealCards,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @DecimalMin("0.1") @DecimalMax("100") BigDecimal deliveryRadiusKm,
        Boolean deliveryEnabled,
        Boolean pickupEnabled,
        @DecimalMin("0") @DecimalMax("60") BigDecimal marketplaceCommissionRate,
        @Valid List<OpeningHourInput> openingHours) {

    public record OpeningHourInput(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String opensAt,
            @NotNull @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String closesAt) {}
}
