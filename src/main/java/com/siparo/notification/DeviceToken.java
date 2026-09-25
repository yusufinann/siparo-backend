package com.siparo.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Push bildirimi hedefi (Expo push token). ownerType: CUSTOMER veya COURIER. */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
public class DeviceToken {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 20)
    private String ownerType;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false, unique = true)
    private String token;

    /** ios / android / web */
    private String platform;

    /** Push metninin dili (tr / en); bildirim metni sunucuda bu dile göre üretilir. */
    private String locale;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastSeenAt = LocalDateTime.now();
}
