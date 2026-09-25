package com.siparo.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

/**
 * Utility class to obtain the currently authenticated customer's UUID.
 * This implementation assumes that the authentication principal is the customer's UUID as a String.
 * Adjust the extraction logic if a different principal type is used (e.g., a custom UserDetails).
 */
public class SecurityUtil {
    public static UUID getCurrentCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.siparo.common.security.CustomUserDetails) {
            return UUID.fromString(((com.siparo.common.security.CustomUserDetails) principal).getId());
        }
        if (principal instanceof String) {
            return UUID.fromString((String) principal);
        }
        // If a custom UserDetails is used, adapt this logic accordingly.
        throw new IllegalStateException("Unsupported principal type: " + principal.getClass().getName());
    }
}
