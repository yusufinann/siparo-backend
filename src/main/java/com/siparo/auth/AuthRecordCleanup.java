package com.siparo.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class AuthRecordCleanup {
    private final JdbcTemplate jdbc;
    @Scheduled(fixedDelay=3600000, initialDelay=3600000)
    public void cleanup() {
        jdbc.update("DELETE FROM auth_rate_limits WHERE window_start < CURRENT_TIMESTAMP - INTERVAL '1 day'");
        jdbc.update("DELETE FROM email_password_resets WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '1 day'");
    }
}
