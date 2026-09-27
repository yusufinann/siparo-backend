package com.siparo.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "email_password_resets") @Getter @Setter
public class EmailReset {
    @Id private String resetKey;
    private String ownerType;
    private UUID ownerId;
    private String codeHash;
    private LocalDateTime expiresAt;
    private int attempts;
    private boolean verified;
    private String grantHash;
    private LocalDateTime grantExpiresAt;
    private boolean used;
}
