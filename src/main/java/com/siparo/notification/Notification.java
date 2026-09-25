package com.siparo.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Bildirim merkezi kaydı. Metin istemcide {@code type} + {@code params} ile (TR/EN) üretilir. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 40)
    private String type;

    private UUID orderId;

    private UUID restaurantId;

    /** JSON nesnesi (örn. {"restaurantName":"…","orderNumber":1042}). */
    private String params;

    private LocalDateTime readAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
