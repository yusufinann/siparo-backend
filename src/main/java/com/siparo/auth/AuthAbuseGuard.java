package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Shared database counters: identical limits for known and unknown accounts, across replicas. */
@Service
@RequiredArgsConstructor
public class AuthAbuseGuard {
    private final JdbcTemplate jdbc;

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
    public void check(String key, int seconds, int maximum, String code) {
        Integer count = jdbc.queryForObject("""
            INSERT INTO auth_rate_limits(bucket_key, window_start, hits) VALUES (?, CURRENT_TIMESTAMP, 1)
            ON CONFLICT (bucket_key) DO UPDATE SET
              hits = CASE WHEN auth_rate_limits.window_start < CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN 1 ELSE auth_rate_limits.hits + 1 END,
              window_start = CASE WHEN auth_rate_limits.window_start < CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN CURRENT_TIMESTAMP ELSE auth_rate_limits.window_start END
            RETURNING hits
            """, Integer.class, hash(key), seconds, seconds);
        if (count != null && count > maximum) throw new BusinessException(code, "Please try again later", HttpStatus.TOO_MANY_REQUESTS);
    }
}
