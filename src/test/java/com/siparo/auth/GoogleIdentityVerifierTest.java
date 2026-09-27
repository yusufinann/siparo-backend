package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleIdentityVerifierTest {
    @Test void verifiesSignatureAndRejectsExpiredSignedTokens() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var verifier = new GoogleIdentityVerifier("mobile", "web");
        var decoder = NimbusJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey) keys.getPublic()).build();
        ReflectionTestUtils.setField(verifier, "decoder", decoder);
        var valid = signed(keys, Instant.now().plusSeconds(600));
        assertThat(verifier.verify("customer", valid).email()).isEqualTo("user@example.com");
        assertThatThrownBy(() -> verifier.verify("customer", signed(keys, Instant.now().minusSeconds(300)))).isInstanceOf(BusinessException.class);
        var attacker = generator.generateKeyPair();
        assertThatThrownBy(() -> verifier.verify("customer", signed(attacker, Instant.now().plusSeconds(600)))).isInstanceOf(BusinessException.class);
    }
    String signed(java.security.KeyPair keys, Instant expiry) throws Exception {
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("https://accounts.google.com").subject("sub").audience("mobile")
            .expirationTime(java.util.Date.from(expiry)).claim("email", "user@example.com").claim("email_verified", true).build();
        var token = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256), claims);
        token.sign(new com.nimbusds.jose.crypto.RSASSASigner(keys.getPrivate())); return token.serialize();
    }
    @Test void rejectsMalformedToken() {
        assertThatThrownBy(() -> new GoogleIdentityVerifier("mobile", "web").verify("customer", "not-a-token"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("GOOGLE_TOKEN_INVALID"));
    }
    @Test void checksAudienceIssuerAndEmailVerification() {
        var verifier = new GoogleIdentityVerifier("mobile", "web"); var decoder = mock(JwtDecoder.class);
        ReflectionTestUtils.setField(verifier, "decoder", decoder);
        for (String issuer : List.of("https://evil.example", "https://accounts.google.com")) {
            when(decoder.decode("token")).thenReturn(jwt(issuer, "wrong", true)); rejects(verifier);
        }
        when(decoder.decode("token")).thenReturn(jwt("https://accounts.google.com", "mobile", false)); rejects(verifier);
        when(decoder.decode("token")).thenReturn(jwt("https://evil.example", "mobile", true)); rejects(verifier);
        when(decoder.decode("token")).thenReturn(jwt("https://accounts.google.com", "mobile", true));
        assertThat(verifier.verify("customer", "token").subject()).isEqualTo("sub");
        assertThatThrownBy(() -> verifier.verify("admin", "token")).isInstanceOf(BusinessException.class);
    }
    Jwt jwt(String issuer, String aud, boolean verified) {
        return Jwt.withTokenValue("token").header("alg", "RS256").issuer(issuer).subject("sub")
            .audience(List.of(aud)).expiresAt(Instant.now().plusSeconds(600)).claim("email", "User@example.com").claim("email_verified", verified).build();
    }
    void rejects(GoogleIdentityVerifier verifier) { assertThatThrownBy(() -> verifier.verify("customer", "token")).isInstanceOf(BusinessException.class); }
}
