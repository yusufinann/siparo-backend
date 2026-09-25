package com.siparo.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("securityService")
public class SecurityService {

    public boolean isRestaurantOwner(Authentication authentication, UUID restaurantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return false;
        }
        
        String userRestaurantId = userDetails.getRestaurantId();
        return userRestaurantId != null && userRestaurantId.equals(restaurantId.toString());
    }
}
