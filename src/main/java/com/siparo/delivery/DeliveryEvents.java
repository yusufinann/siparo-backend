package com.siparo.delivery;

import java.util.UUID;

/** Kurye tarafı gerçek zamanlı/push olayları (commit sonrası yayınlanır). */
public final class DeliveryEvents {
    private DeliveryEvents() {}

    /** type: ASSIGNED, CANCELLED, REASSIGNED */
    public record CourierDeliveryChanged(UUID courierId, UUID assignmentId, UUID orderId, String type, Long orderNumber,
                                         String restaurantName) {}
}
