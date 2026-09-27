package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.util.PhoneNumbers;
import com.siparo.customer.*;
import com.siparo.restaurant.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class GoogleAuthService {
    private final GoogleIdentityVerifier verifier;
    private final CustomerRepository customers;
    private final RestaurantRepository restaurants;
    private final RestaurantService restaurantService;
    private final AuthService sessions;
    private final PasswordEncoder encoder;
    private final AuthAbuseGuard guard;
    private final JdbcTemplate jdbc;

    @Transactional
    public AuthService.Session login(String role, ExtendedAuthRequests.Google request, String ip) {
        guard.check("google-ip:" + ip, 600, 30, "AUTH_RATE_LIMITED");
        var identity = verifier.verify(role, request.idToken());
        guard.check("google-sub:" + role + identity.subject(), 600, 20, "AUTH_RATE_LIMITED");
        // Serialize concurrent first sign-ins on all backend instances.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, role + ":" + identity.email());
        if (role.equals("customer")) {
            var existing = customers.findByGoogleSubject(identity.subject());
            if (existing.isPresent()) {
                if (existing.get().isDeleted()) throw error("GOOGLE_TOKEN_INVALID");
                return sessions.customerSession(existing.get());
            }
            var customer = customers.findByEmailIgnoreCase(identity.email()).orElse(null);
            if (customer != null) {
                if (customer.isDeleted()) throw error("GOOGLE_TOKEN_INVALID");
                link(customer.getGoogleSubject(), customer.getPasswordHash(), request.password());
            } else {
                requireProfile(request, true);
                String phone = PhoneNumbers.normalize(request.phoneNumber());
                if (customers.findByPhoneNumber(phone).isPresent()) throw error("GOOGLE_PHONE_CONFLICT");
                customer = new Customer(); customer.setPhoneNumber(phone);
                customer.setFullName(identity.name() == null ? identity.email() : identity.name());
                customer.setEmail(identity.email()); customer.setTermsAcceptedAt(LocalDateTime.now()); customer.setKvkkAcceptedAt(LocalDateTime.now());
            }
            customer.setGoogleSubject(identity.subject()); customers.saveAndFlush(customer);
            return sessions.customerSession(customer);
        }
        var existing = restaurants.findByGoogleSubject(identity.subject());
        if (existing.isPresent()) return sessions.restaurantSession(existing.get());
        var restaurant = restaurants.findByEmailIgnoreCase(identity.email()).orElse(null);
        if (restaurant != null) link(restaurant.getGoogleSubject(), restaurant.getPasswordHash(), request.password());
        else {
            requireProfile(request, false);
            String phone = PhoneNumbers.normalize(request.phoneNumber());
            if (restaurants.existsByOwnerPhone(phone)) throw error("GOOGLE_PHONE_CONFLICT");
            restaurant = new Restaurant(); restaurant.setOwnerPhone(phone); restaurant.setPhone(phone);
            restaurant.setEmail(identity.email()); restaurant.setName(request.restaurantName().trim());
            restaurant.setPublicCode(restaurantService.newPublicCode()); restaurant.setStatus("INACTIVE");
            restaurant.setMinOrderAmount(BigDecimal.ZERO); restaurant.setDeliveryFee(BigDecimal.ZERO);
        }
        restaurant.setGoogleSubject(identity.subject()); restaurants.saveAndFlush(restaurant);
        return sessions.restaurantSession(restaurant);
    }
    private void link(String subject, String hash, String password) {
        if (subject != null || hash == null) throw error("GOOGLE_LINK_DENIED");
        if (password == null || password.isBlank()) throw error("GOOGLE_LINK_REQUIRED");
        if (!encoder.matches(password, hash)) throw error("INVALID_CREDENTIALS");
    }
    private void requireProfile(ExtendedAuthRequests.Google r, boolean customer) {
        if (r.phoneNumber() == null || r.phoneNumber().isBlank()
            || (customer && (!Boolean.TRUE.equals(r.termsAccepted()) || !Boolean.TRUE.equals(r.kvkkAccepted())))
            || (!customer && (r.restaurantName() == null || r.restaurantName().isBlank()))) throw error("GOOGLE_PROFILE_REQUIRED");
    }
    private BusinessException error(String code) { return new BusinessException(code, "Google sign-in requires additional verification", HttpStatus.BAD_REQUEST); }
}
