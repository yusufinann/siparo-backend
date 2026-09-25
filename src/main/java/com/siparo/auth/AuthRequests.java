package com.siparo.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}

    public record Login(@NotBlank @Size(max = 50) String phoneNumber, @NotBlank @Size(max = 100) String password) {}

    public record CustomerRegister(
            @NotBlank @Size(max = 50) String phoneNumber,
            @NotBlank @Size(max = 255) String fullName,
            @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @AssertTrue Boolean termsAccepted,
            @AssertTrue Boolean kvkkAccepted) {}

    public record RestaurantRegister(
            @NotBlank @Size(max = 255) String restaurantName,
            @NotBlank @Size(max = 50) String phoneNumber,
            @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 6, max = 100) String password) {}

    public record ResetRequest(@NotBlank @Size(max = 50) String phoneNumber) {}

    public record ResetConfirm(
            @NotBlank @Size(max = 50) String phoneNumber,
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank @Size(min = 6, max = 100) String newPassword) {}
}
