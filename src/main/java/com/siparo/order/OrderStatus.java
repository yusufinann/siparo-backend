package com.siparo.order;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING,
    /** Eski akıştan kalan değer; yeni siparişlerde onay doğrudan PREPARING'e geçer. */
    CONFIRMED,
    PREPARING,
    /** Yalnızca gel-al siparişleri: restoran "hazır" işaretledi, müşteri teslim alacak. */
    READY_FOR_PICKUP,
    ON_THE_WAY,
    DELIVERED,
    CANCELLED;

    public Set<OrderStatus> nextStatuses() {
        return switch (this) {
            case PENDING, CONFIRMED -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(ON_THE_WAY, READY_FOR_PICKUP, CANCELLED);
            case READY_FOR_PICKUP -> EnumSet.of(DELIVERED, CANCELLED);
            case ON_THE_WAY -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return nextStatuses().contains(next);
    }

    public boolean isActive() {
        return this != DELIVERED && this != CANCELLED;
    }

    public static final Set<String> ACTIVE_NAMES = Set.of(
            PENDING.name(), CONFIRMED.name(), PREPARING.name(), READY_FOR_PICKUP.name(), ON_THE_WAY.name());
}
