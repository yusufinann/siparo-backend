package com.siparo.auth;

import jakarta.validation.constraints.*;

public final class ExtendedAuthRequests {
    private ExtendedAuthRequests() {}
    public record ResetRequest(@NotBlank @Email @Size(max=255) String email, @Pattern(regexp="tr|en") String language) {}
    public record Verify(@NotBlank @Email @Size(max=255) String email, @NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record Confirm(@NotBlank @Size(max=100) String resetToken, @NotBlank @Size(min=6,max=100) String newPassword) {}
    public record Google(@NotBlank @Size(max=10000) String idToken,
        @Size(max=100) String password, @Size(max=50) String phoneNumber,
        @Size(max=255) String restaurantName, Boolean termsAccepted, Boolean kvkkAccepted) {}
}
