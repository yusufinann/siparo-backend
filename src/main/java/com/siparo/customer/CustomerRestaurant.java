package com.siparo.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Müşterinin "Restoranlarım" listesi ve restoranın müşteri edinim kaydı.
 * Listeden çıkarma {@code removedAt} ile yapılır; kayıt silinmez (edinim metrikleri korunur).
 */
@Entity
@Table(name = "customer_restaurants")
@Getter
@Setter
@NoArgsConstructor
public class CustomerRestaurant {

    @EmbeddedId
    private CustomerRestaurantId id = new CustomerRestaurantId();

    /** QR, LINK (davet bağlantısı / deep link), MANUAL (kod elle girildi). */
    @Column(nullable = false)
    private String source = "MANUAL";

    private boolean favorite;

    private LocalDateTime removedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class CustomerRestaurantId implements Serializable {
        @Column(name = "customer_id")
        private UUID customerId;

        @Column(name = "restaurant_id")
        private UUID restaurantId;

        public CustomerRestaurantId(UUID customerId, UUID restaurantId) {
            this.customerId = customerId;
            this.restaurantId = restaurantId;
        }
    }
}
