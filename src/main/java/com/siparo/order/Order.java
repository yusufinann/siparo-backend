package com.siparo.order;

import com.siparo.customer.Customer;
import com.siparo.restaurant.Restaurant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JPQL'de "Order" ayrılmış kelime olduğu için varlık adı CustomerOrder'dır (tablo: orders). */
@Entity(name = "CustomerOrder")
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    private UUID id = UUID.randomUUID();

    /** Okunabilir sipariş numarası (#SP-1042). */
    @Column(nullable = false, unique = true, updatable = false)
    private Long orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String status;

    /** DELIVERY veya PICKUP. */
    @Column(nullable = false)
    private String fulfillmentType = "DELIVERY";

    private String deliveryAddress;

    private String deliveryAddressDetail;

    private String cancelReason;

    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private UUID couponId;

    private String couponCode;

    private String paymentMethod;

    private String note;

    private Double deliveryLatitude;

    private Double deliveryLongitude;

    private Instant estimatedDeliveryAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 50)
    private List<OrderStatusEvent> statusEvents = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public boolean isPickup() {
        return "PICKUP".equals(fulfillmentType);
    }
}
