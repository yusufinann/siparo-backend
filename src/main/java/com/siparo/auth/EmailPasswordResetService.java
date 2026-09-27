package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import com.siparo.customer.CustomerRepository;
import com.siparo.restaurant.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class EmailPasswordResetService {
    private final EmailResetRepository resets;
    private final CustomerRepository customers;
    private final RestaurantRepository restaurants;
    private final PasswordEncoder encoder;
    private final PasswordResetMailService mail;
    private final RefreshTokenService refresh;
    private final AuthAbuseGuard guard;
    private static final SecureRandom RANDOM = new SecureRandom();
    @Value("${siparo.auth.reset-secret:}") private String resetSecret;

    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String key(String role, String email) { return AuthAbuseGuard.hash(role + ":" + normalize(email)); }
    private String protectedCode(String code) {
        if (resetSecret == null || resetSecret.length() < 32)
            throw new BusinessException("FEATURE_UNAVAILABLE", "Reset secret is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(resetSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    @Transactional
    public Map<String, Object> request(String role, ExtendedAuthRequests.ResetRequest request, String ip) {
        mail.requireConfigured();
        String email = normalize(request.email());
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String protectedCode = protectedCode(code);
        guard.check("reset-ip:" + ip, 3600, 30, "RESET_REQUEST_RATE_LIMITED");
        guard.check("reset-hour:" + role + email, 3600, 5, "RESET_REQUEST_RATE_LIMITED");
        guard.check("reset-cooldown:" + role + email, 60, 1, "RESET_REQUEST_RATE_LIMITED");
        var reset = resets.lockByKey(key(role, email)).orElseGet(EmailReset::new);
        reset.setResetKey(key(role, email)); reset.setOwnerType(role);
        UUID owner = role.equals("customer")
            ? customers.findByEmailIgnoreCase(email).filter(c -> !c.isDeleted() && c.getPasswordHash() != null).map(c -> c.getId()).orElse(null)
            : restaurants.findByEmailIgnoreCase(email).filter(r -> r.getPasswordHash() != null).map(r -> r.getId()).orElse(null);
        reset.setOwnerId(owner); reset.setCodeHash(encoder.encode(protectedCode));
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(10)); reset.setAttempts(0);
        reset.setUsed(false); reset.setVerified(false); reset.setGrantHash(null); reset.setGrantExpiresAt(null);
        resets.save(reset);
        if (owner != null) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { mail.send(email, code, request.language()); }
                catch (org.springframework.core.task.TaskRejectedException ignored) {
                    org.slf4j.LoggerFactory.getLogger(EmailPasswordResetService.class).error("Password reset mail queue is full");
                }
            }
        });
        return Map.of("expiresInSeconds", 600, "resendAfterSeconds", 60);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Map<String, String> verify(String role, ExtendedAuthRequests.Verify request, String ip) {
        guard.check("verify-ip:" + ip, 600, 60, "RESET_TOO_MANY_ATTEMPTS");
        var reset = resets.lockByKey(key(role, request.email())).orElseThrow(() -> error("RESET_CODE_INVALID"));
        if (reset.isUsed() || reset.isVerified()) throw error("RESET_CODE_INVALID");
        if (reset.getExpiresAt().isBefore(LocalDateTime.now())) throw error("RESET_CODE_EXPIRED");
        if (reset.getAttempts() >= 5) throw error("RESET_TOO_MANY_ATTEMPTS");
        reset.setAttempts(reset.getAttempts() + 1);
        boolean matches = encoder.matches(protectedCode(request.code()), reset.getCodeHash());
        if (!matches || reset.getOwnerId() == null) throw error(reset.getAttempts() >= 5 ? "RESET_TOO_MANY_ATTEMPTS" : "RESET_CODE_INVALID");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        reset.setVerified(true); reset.setGrantHash(AuthAbuseGuard.hash(token));
        reset.setGrantExpiresAt(LocalDateTime.now().plusMinutes(5));
        return Map.of("resetToken", token);
    }

    @Transactional
    public void confirm(String role, ExtendedAuthRequests.Confirm request, String ip) {
        guard.check("confirm-ip:" + ip, 600, 30, "RESET_TOO_MANY_ATTEMPTS");
        var reset = resets.lockByGrant(AuthAbuseGuard.hash(request.resetToken())).orElseThrow(() -> error("RESET_TOKEN_INVALID"));
        if (!role.equals(reset.getOwnerType()) || reset.isUsed() || !reset.isVerified()
            || reset.getGrantExpiresAt().isBefore(LocalDateTime.now())) throw error("RESET_TOKEN_INVALID");
        String password = encoder.encode(request.newPassword());
        // Millisecond authTime claim supports immediate login after revocation.
        var invalidBefore = LocalDateTime.now();
        if (role.equals("customer")) {
            var customer = customers.findById(reset.getOwnerId()).filter(c -> !c.isDeleted() && c.getPasswordHash() != null)
                .orElseThrow(() -> error("RESET_TOKEN_INVALID"));
            if (customer.getEmail() == null || !key(role, customer.getEmail()).equals(reset.getResetKey())) throw error("RESET_TOKEN_INVALID");
            customer.setPasswordHash(password); customer.setSessionsInvalidBefore(invalidBefore);
            refresh.revokeAll(RefreshTokenService.OWNER_CUSTOMER, customer.getId());
        } else {
            var restaurant = restaurants.findById(reset.getOwnerId()).filter(r -> r.getPasswordHash() != null)
                .orElseThrow(() -> error("RESET_TOKEN_INVALID"));
            if (restaurant.getEmail() == null || !key(role, restaurant.getEmail()).equals(reset.getResetKey())) throw error("RESET_TOKEN_INVALID");
            restaurant.setPasswordHash(password); restaurant.setSessionsInvalidBefore(invalidBefore);
        }
        reset.setUsed(true); reset.setGrantHash(null); reset.setCodeHash("");
    }
    private BusinessException error(String code) { return new BusinessException(code, "Password reset could not be completed"); }
}
