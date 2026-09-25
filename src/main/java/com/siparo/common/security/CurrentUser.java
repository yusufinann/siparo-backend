package com.siparo.common.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id(Authentication authentication) {
        return UUID.fromString(((CustomUserDetails) authentication.getPrincipal()).getId());
    }

    /** İşletme yöneticisinin kimliği "admin-{restaurantId}" biçimindedir; olay kayıtlarında olduğu gibi kullanılır. */
    public static String rawId(Authentication authentication) {
        return ((CustomUserDetails) authentication.getPrincipal()).getId();
    }
}
