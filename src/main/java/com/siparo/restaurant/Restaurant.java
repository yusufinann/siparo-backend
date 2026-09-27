package com.siparo.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    @Id
    private UUID id = UUID.randomUUID();

    /** Herkese açık kısa kod (QR / davet bağlantısı). */
    @Column(nullable = false, unique = true, length = 12)
    private String publicCode;

    private String name;
    private String description;
    private String logoUrl;
    private String coverUrl;
    /** Müşteri ve kuryelerin gördüğü iletişim telefonu. */
    private String phone;
    /** İşletme sahibinin giriş telefonu (ayarlardan değişmez). */
    private String ownerPhone;
    private String email;
    private String passwordHash;
    private String googleSubject;
    private LocalDateTime sessionsInvalidBefore;
    private String address;
    /** ACTIVE: sipariş kabul ediyor, INACTIVE: işletme siparişleri geçici olarak durdurdu. */
    private String status;
    private BigDecimal minOrderAmount;
    private BigDecimal deliveryFee;

    private Integer deliveryTimeMin;
    private Integer deliveryTimeMax;

    /** Yalnızca gerçek değerlendirmelerden hesaplanır; değerlendirme yoksa null. */
    private BigDecimal ratingScore;
    private Integer ratingCount = 0;
    private String tags;
    private BigDecimal freeDeliveryThreshold = BigDecimal.ZERO;
    /** Virgülle ayrılmış PaymentMethod değerleri. */
    private String paymentMethods;
    /** Virgülle ayrılmış MealCard değerleri; yalnızca MEAL_CARD_ON_DELIVERY kabul ediliyorsa dolu. */
    private String mealCards;

    private Double latitude;
    private Double longitude;
    private BigDecimal deliveryRadiusKm;
    private boolean deliveryEnabled = true;
    private boolean pickupEnabled = false;

    /** İşletmenin beyan ettiği pazaryeri komisyon oranı (%); yalnızca işletmeye gösterilen tasarruf tahmini içindir. */
    private BigDecimal marketplaceCommissionRate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
