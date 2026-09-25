package com.siparo.order.realtime;

import java.util.UUID;

public record OrderStatusChangedEvent(UUID restaurantId, UUID customerId, UUID orderId, String status) {}
