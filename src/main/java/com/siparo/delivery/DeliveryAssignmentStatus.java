package com.siparo.delivery;

public enum DeliveryAssignmentStatus {
    ASSIGNED,
    ACCEPTED,
    ON_THE_WAY,
    DELIVERED,
    REJECTED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DELIVERED || this == REJECTED || this == FAILED || this == CANCELLED;
    }
}
