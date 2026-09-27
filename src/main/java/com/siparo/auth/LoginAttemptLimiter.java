package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hesap başına kaba kuvvet koruması: aynı giriş anahtarı (rol + telefon) için pencere içinde en fazla
 * {@link #MAX_FAILURES} hatalı deneme. Başarılı girişte sayaç sıfırlanır. Tek sunucu örneği için bellek içi tutulur.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /** Kilitliyse LOGIN_RATE_LIMITED (429) fırlatır; {@code retryAfterSeconds} parametresiyle. */
    public void check(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) return;
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= MAX_FAILURES) {
                long retryAfter = Math.max(1, Duration.between(Instant.now(), attempts.peekFirst().plus(WINDOW)).toSeconds());
                throw new BusinessException("LOGIN_RATE_LIMITED", "Too many failed login attempts", HttpStatus.TOO_MANY_REQUESTS,
                        Map.of("retryAfterSeconds", retryAfter, "retryAfterMinutes", (retryAfter + 59) / 60));
            }
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > MAX_TRACKED_KEYS) failures.entrySet().removeIf(entry -> isStale(entry.getValue()));
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(Instant.now());
        }
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    private static void prune(Deque<Instant> attempts) {
        Instant threshold = Instant.now().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(threshold)) attempts.pollFirst();
    }

    private static boolean isStale(Deque<Instant> attempts) {
        synchronized (attempts) {
            prune(attempts);
            return attempts.isEmpty();
        }
    }
}
