package com.siparo.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_codes")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetCode {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String codeHash;

    private int attempts;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime consumedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
