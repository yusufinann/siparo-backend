package com.siparo.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDto {
    private UUID id;
    private Long orderNumber;
    private UUID restaurantId;
    private String restaurantName;
    private String restaurantLogoUrl;
    private String restaurantPhone;
    private String restaurantAddress;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private String status;
    private String fulfillmentType;
    private String deliveryAddress;
    private String deliveryAddressDetail;
    private Double deliveryLatitude;
    private Double deliveryLongitude;
    private String cancelReason;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal discountAmount;
    private String couponCode;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String note;
    private LocalDateTime createdAt;
    private Instant estimatedDeliveryAt;
    private List<OrderStatusEventDto> statusHistory;
    private List<OrderItemDto> items;

    // Müşteri bağlamı
    private Integer reviewRating;
    private Boolean canReview;
    private Boolean canCancel;
    private IssueSummary issue;

    // İşletme bağlamı
    private DeliverySummary delivery;
    private Boolean firstOrder;

    @Getter
    @Setter
    public static class OrderStatusEventDto {
        private String status;
        private String reason;
        private Instant createdAt;
    }

    @Getter
    @Setter
    public static class OrderItemDto {
        private UUID id;
        private UUID menuItemId;
        private String menuItemName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String note;
        private List<OptionSnapshot> options;
    }

    @Getter
    @Setter
    public static class OptionSnapshot {
        private UUID id;
        private String groupName;
        private String name;
        private BigDecimal priceDelta;
    }

    @Getter
    @Setter
    public static class DeliverySummary {
        private UUID assignmentId;
        private UUID courierId;
        private String courierName;
        private String status;
    }

    @Getter
    @Setter
    public static class IssueSummary {
        private UUID id;
        private String type;
        private String status;
        private String resolutionNote;
    }
}
