package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Dönen yenileme token'ları. Her yenilemede eski token iptal edilir ve yenisi verilir. Döndürülmüş bir token geç yeniden
 * kullanılırsa (çalınma belirtisi) sahibin tüm oturumları kapatılır; kısa süre içindeki eşzamanlı yenileme (ön plan + arka plan
 * konum görevi) zararsızdır. Çıkış veya şifre değişikliğiyle iptal edilmiş token yalnızca reddedilir.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    public static final String OWNER_CUSTOMER = "CUSTOMER";
    public static final String OWNER_COURIER = "COURIER";
    private static final int TOKEN_BYTES = 32;
    private static final long CONCURRENT_REFRESH_GRACE_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;

    @Value("${jwt.refresh-expiration-days:60}")
    private long refreshExpirationDays;

    public record Owner(String type, UUID id) {}

    public record Rotation(Owner owner, String refreshToken) {}

    @Transactional
    public String issue(String ownerType, UUID ownerId) {
        return create(ownerType, ownerId).raw();
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = repository.findForUpdate(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);
        LocalDateTime now = LocalDateTime.now();
        Owner owner = new Owner(current.getOwnerType(), current.getOwnerId());
        if (current.getRevokedAt() != null) {
            // Çıkış/şifre değişikliği/hesap silme ile iptal edilmiş token: yalnızca reddedilir (diğer oturumlara dokunulmaz).
            if (current.getReplacedBy() == null) throw invalid();
            // Döndürülmüş token kısa süre içinde tekrar geldi: eşzamanlı yenileme (ön plan + arka plan görevi), zararsız.
            if (current.getRevokedAt().isAfter(now.minusSeconds(CONCURRENT_REFRESH_GRACE_SECONDS))) {
                return new Rotation(owner, create(owner.type(), owner.id()).raw());
            }
            // Döndürülmüş token geç yeniden kullanıldı: çalınma belirtisi, sahibin tüm oturumları kapatılır.
            repository.revokeAll(owner.type(), owner.id(), now);
            throw invalid();
        }
        if (current.getExpiresAt().isBefore(now)) {
            current.setRevokedAt(now);
            throw invalid();
        }
        Created next = create(owner.type(), owner.id());
        current.setRevokedAt(now);
        current.setReplacedBy(next.id());
        return new Rotation(owner, next.raw());
    }

    /** Çıkış: yalnızca bu cihazın token'ı. Bilinmeyen token sessizce yok sayılır. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findForUpdate(hash(rawToken)).filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(LocalDateTime.now()));
    }

    /** Şifre değişikliği, şifre sıfırlama, hesap silme: sahibin tüm cihazlarındaki oturumlar kapanır. */
    @Transactional
    public void revokeAll(String ownerType, UUID ownerId) {
        repository.revokeAll(ownerType, ownerId, LocalDateTime.now());
    }

    private record Created(UUID id, String raw) {}

    private Created create(String ownerType, UUID ownerId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(raw));
        token.setOwnerType(ownerType);
        token.setOwnerId(ownerId);
        token.setExpiresAt(LocalDateTime.now().plusDays(refreshExpirationDays));
        repository.save(token);
        return new Created(token.getId(), raw);
    }

    private static String hash(String raw) {
        if (raw == null || raw.isBlank()) throw invalid();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BusinessException invalid() {
        return new BusinessException("INVALID_REFRESH_TOKEN", "Session has expired", HttpStatus.UNAUTHORIZED);
    }
}
