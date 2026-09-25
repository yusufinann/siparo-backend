package com.siparo.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CustomerRequests {
    private CustomerRequests() {}

    public record UpdateProfile(
            @NotBlank @Size(max = 255) String fullName,
            @Email @Size(max = 255) String email) {}

    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 6, max = 100) String newPassword) {}

    public record DeleteAccount(@NotBlank String password) {}

    public record NotificationPreferences(
            @NotNull Boolean orderUpdates,
            @NotNull Boolean campaigns,
            @NotNull Boolean restaurantNews) {}

    public record LinkRestaurant(@Pattern(regexp = "QR|LINK|MANUAL") String source) {}

    public record Favorite(@NotNull Boolean favorite) {}
}
