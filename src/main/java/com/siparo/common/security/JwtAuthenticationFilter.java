package com.siparo.common.security;

import com.siparo.customer.CustomerRepository;
import com.siparo.delivery.CourierRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomerRepository customerRepository;
    private final CourierRepository courierRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        try {
            String username = jwtService.extractUsername(jwt);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String role = jwtService.extractClaim(jwt, claims -> claims.get("role", String.class));
                String userId = jwtService.extractUserId(jwt);
                String restaurantId = jwtService.extractRestaurantId(jwt);

                CustomUserDetails userDetails = new CustomUserDetails(
                        userId, username, "", restaurantId, Collections.singleton(new SimpleGrantedAuthority(role)));

                if (jwtService.isTokenValid(jwt, userDetails) && isPrincipalActive(role, userId)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Geçersiz token: kimlik doğrulanmaz, korumalı uçlar 401 döner.
        }

        filterChain.doFilter(request, response);
    }

    /** Silinmiş müşteri hesabı ve pasifleştirilmiş kurye, süresi dolmamış token ile de erişemez. */
    private boolean isPrincipalActive(String role, String userId) {
        if ("ROLE_CUSTOMER".equals(role)) {
            return customerRepository.findById(UUID.fromString(userId)).map(customer -> !customer.isDeleted()).orElse(false);
        }
        if ("ROLE_COURIER".equals(role)) {
            return courierRepository.findById(UUID.fromString(userId)).map(courier -> courier.isActive()).orElse(false);
        }
        return true;
    }
}
