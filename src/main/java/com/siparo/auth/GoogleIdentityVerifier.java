package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GoogleIdentityVerifier {
    private final JwtDecoder decoder;
    private final List<String> customerAudiences;
    private final List<String> businessAudiences;
    public record Identity(String subject, String email, String name) {}
    public GoogleIdentityVerifier(@Value("${siparo.auth.google.customer-client-ids:}") String customerIds,
                                  @Value("${siparo.auth.google.business-client-ids:}") String businessIds) {
        customerAudiences = parse(customerIds); businessAudiences = parse(businessIds);
        var google = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        google.setJwtValidator(JwtValidators.createDefault()); decoder = google;
    }
    private List<String> parse(String ids) { return Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList(); }
    public Identity verify(String role, String token) {
        var audiences = role.equals("customer") ? customerAudiences : businessAudiences;
        if (audiences.isEmpty()) throw new BusinessException("FEATURE_UNAVAILABLE", "Google login is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        try {
            var jwt = decoder.decode(token);
            String issuer = jwt.getClaimAsString("iss");
            String email = jwt.getClaimAsString("email");
            if (!("https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer))
                || jwt.getAudience().stream().noneMatch(audiences::contains)
                || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                || jwt.getExpiresAt() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                || email == null || email.length() > 255) throw new IllegalArgumentException();
            return new Identity(jwt.getSubject(), EmailPasswordResetService.normalize(email), jwt.getClaimAsString("name"));
        } catch (Exception ex) {
            throw new BusinessException("GOOGLE_TOKEN_INVALID", "Google identity could not be verified", HttpStatus.UNAUTHORIZED);
        }
    }
}
