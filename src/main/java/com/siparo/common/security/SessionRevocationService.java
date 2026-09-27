package com.siparo.common.security;

import com.siparo.customer.CustomerRepository;
import com.siparo.restaurant.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.UUID;

/** Shared by HTTP and realtime channels; database cutoff also works across backend replicas. */
@Service @RequiredArgsConstructor
public class SessionRevocationService {
    private final CustomerRepository customers;
    private final RestaurantRepository restaurants;
    private final JwtService jwt;
    public boolean valid(String token) {
        try {
            String role = jwt.extractClaim(token, claims -> claims.get("role", String.class));
            if ("ROLE_CUSTOMER".equals(role)) return customers.findById(UUID.fromString(jwt.extractUserId(token)))
                .map(c -> !c.isDeleted() && issuedAfter(token, c.getSessionsInvalidBefore())).orElse(false);
            if ("ROLE_RESTAURANT_ADMIN".equals(role)) return restaurants.findById(UUID.fromString(jwt.extractRestaurantId(token)))
                .map(r -> issuedAfter(token, r.getSessionsInvalidBefore())).orElse(false);
            return true;
        } catch (Exception ignored) { return false; }
    }
    private boolean issuedAfter(String token, LocalDateTime cutoff) {
        if (cutoff == null) return true;
        Long authTime = jwt.extractClaim(token, claims -> claims.get("authTime", Long.class));
        java.util.Date issued = jwt.extractClaim(token, io.jsonwebtoken.Claims::getIssuedAt);
        Instant created = authTime == null ? (issued == null ? Instant.EPOCH : issued.toInstant()) : Instant.ofEpochMilli(authTime);
        return created.isAfter(cutoff.atZone(ZoneId.systemDefault()).toInstant());
    }
}
