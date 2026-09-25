package com.siparo.order.realtime;

import com.siparo.delivery.DeliveryEvents;
import com.siparo.feedback.FeedbackEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderRealtimeEventListener {

    private final OrderRealtimeHandler realtimeHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        realtimeHandler.publishOrderCreated(event.restaurantId(), event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        realtimeHandler.publishOrderStatusChanged(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCourierLocationChanged(CourierLocationChangedEvent event) {
        realtimeHandler.publishCourierLocationChanged(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCourierDeliveryChanged(DeliveryEvents.CourierDeliveryChanged event) {
        realtimeHandler.publishCourierDeliveryChanged(event.courierId(), event.assignmentId(), event.orderId(), event.type());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueReported(FeedbackEvents.IssueReported event) {
        realtimeHandler.publishRestaurantEvent(event.restaurantId(), "ISSUE_REPORTED", event.orderId());
    }
}
