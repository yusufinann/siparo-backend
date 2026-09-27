package com.siparo.restaurant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Restoranın dış görünümü. Müşteri bağlamı (mesafe, favori, kaynak) ve işletmeye özel alanlar (e-posta, komisyon)
 * yalnızca ilgili uçlarda doldurulur; aksi halde yanıtta yer almaz.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RestaurantDto {
    private UUID id;
    private String publicCode;
    private String name;
    private String description;
    private String logoUrl;
    private String coverUrl;
    private String phone;
    private String address;
    private String status;
    private boolean open;
    /** OPEN, CLOSED (çalışma saati dışı), TEMPORARILY_CLOSED (işletme siparişleri durdurdu). */
    private String displayStatus;
    private Instant nextOpeningAt;
    private BigDecimal minOrderAmount;
    private BigDecimal deliveryFee;
    private BigDecimal freeDeliveryThreshold;
    private Integer deliveryTimeMin;
    private Integer deliveryTimeMax;
    private BigDecimal ratingScore;
    private Integer ratingCount;
    private String tags;
    private String paymentMethods;
    private String mealCards;
    private List<OpeningHourDto> openingHours;
    private Double latitude;
    private Double longitude;
    private BigDecimal deliveryRadiusKm;
    private boolean deliveryEnabled;
    private boolean pickupEnabled;

    // Müşteri bağlamı
    private Double distanceKm;
    private Boolean withinDeliveryArea;
    private Boolean favorite;
    private String source;
    private LocalDateTime addedAt;
    private LocalDateTime lastOrderedAt;

    // Yalnızca işletme sahibine
    private String email;
    private String ownerPhone;
    private BigDecimal marketplaceCommissionRate;

    @Getter
    @Setter
    public static class OpeningHourDto {
        private int dayOfWeek;
        private String opensAt;
        private String closesAt;
    }
}
