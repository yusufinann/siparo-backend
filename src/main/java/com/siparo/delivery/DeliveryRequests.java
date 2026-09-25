package com.siparo.delivery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DeliveryRequests {
    private DeliveryRequests() {}

    public record CreateCourier(
            @NotBlank @Size(max = 255) String fullName,
            @NotBlank @Size(max = 50) String phoneNumber,
            @NotBlank @Size(min = 6, max = 100) String temporaryPassword) {}

    public record UpdateCourier(@Size(max = 255) String fullName, Boolean active) {}

    public record ResetPassword(@NotBlank @Size(min = 6, max = 100) String temporaryPassword) {}

    /**
     * Telefon + şifre yeterlidir. Aynı telefon birden fazla restoranda kayıtlıysa restoran kodu (QR'daki kısa kod) istenir.
     * {@code restaurantId} eski istemciler için korunur.
     */
    public record CourierLogin(
            UUID restaurantId,
            @Size(max = 12) String restaurantCode,
            @NotBlank String phoneNumber,
            @NotBlank String password) {}

    public record AssignCourier(@NotNull UUID courierId) {}

    public record Reason(@NotBlank @Size(max = 100) String reason) {}

    public record Complete(@NotBlank @Size(min = 6, max = 6) String pin) {}

    public record Availability(@NotBlank String status) {}

    public record Location(
            @NotNull @Min(-90) @Max(90) Double latitude,
            @NotNull @Min(-180) @Max(180) Double longitude,
            @Min(0) Double accuracy,
            @Min(0) @Max(360) Double heading,
            @Min(0) Double speed,
            @NotNull Instant recordedAt) {}
}
