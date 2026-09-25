package com.siparo.order.realtime;

import java.time.Instant;
import java.util.UUID;

public record CourierLocationChangedEvent(
        UUID customerId, UUID orderId, double latitude, double longitude,
        Double accuracy, Double heading, Double speed, Instant recordedAt) {}
