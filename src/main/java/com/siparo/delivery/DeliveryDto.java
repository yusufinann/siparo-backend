package com.siparo.delivery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.siparo.order.OrderDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Kuryenin tek bakışta ihtiyaç duyduğu her şey: alım noktası, teslim noktası, sipariş özeti, tahsilat. */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliveryDto {
    private UUID id;
    private UUID orderId;
    private Long orderNumber;
    private UUID restaurantId;
    private String restaurantName;
    private String restaurantPhone;
    private String restaurantAddress;
    private Double restaurantLatitude;
    private Double restaurantLongitude;
    private UUID courierId;
    private String courierName;
    private String courierPhone;
    private String status;
    private String orderStatus;
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private String deliveryAddressDetail;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private String orderNote;
    private String paymentMethod;
    /** Kapıda tahsil edilecek tutar (online ödemede 0). */
    private BigDecimal amountToCollect;
    private BigDecimal orderTotal;
    private List<OrderDto.OrderItemDto> items;
    private Instant estimatedDeliveryAt;
    private String failureReason;
    private Instant assignedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private LocationDto lastLocation;

    @Getter
    @Setter
    public static class LocationDto {
        private Double latitude;
        private Double longitude;
        private Double accuracy;
        private Double heading;
        private Double speed;
        private Instant recordedAt;
    }
}
