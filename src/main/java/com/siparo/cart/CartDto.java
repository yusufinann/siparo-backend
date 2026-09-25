package com.siparo.cart;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Sunucuda hesaplanmış sepet: tutarlar, kupon sonucu ve siparişi engelleyen durumlar. */
@Getter
@Setter
public class CartDto {
    private UUID id;
    private UUID restaurantId;
    private RestaurantSummary restaurant;
    private List<CartItemDto> items;
    private int itemCount;
    /** "DELIVERY" veya "PICKUP": tutarların hangi teslim türü için hesaplandığı. */
    private String fulfillmentType;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private BigDecimal total;
    private BigDecimal freeDeliveryRemaining;
    private BigDecimal minOrderRemaining;
    private AppliedCoupon coupon;
    /** Siparişi engelleyen kodlar: RESTAURANT_CLOSED, RESTAURANT_TEMPORARILY_CLOSED, MENU_ITEM_UNAVAILABLE, BELOW_MIN_ORDER. */
    private List<String> blockers;

    @Getter
    @Setter
    public static class RestaurantSummary {
        private UUID id;
        private String name;
        private String logoUrl;
        private boolean open;
        private String displayStatus;
        private BigDecimal minOrderAmount;
        private BigDecimal deliveryFee;
        private BigDecimal freeDeliveryThreshold;
        private Integer deliveryTimeMin;
        private Integer deliveryTimeMax;
        private boolean deliveryEnabled;
        private boolean pickupEnabled;
    }

    @Getter
    @Setter
    public static class AppliedCoupon {
        private String code;
        private String title;
        private String discountType;
        private boolean valid;
        private String errorCode;
        private java.util.Map<String, Object> errorParams;
    }
}
