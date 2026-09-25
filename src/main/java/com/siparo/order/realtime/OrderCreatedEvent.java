package com.siparo.order.realtime;

import java.util.UUID;

public record OrderCreatedEvent(UUID restaurantId, UUID orderId) {}
