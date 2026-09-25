package com.siparo.delivery;

import com.siparo.common.exception.BusinessException;
import com.siparo.order.Order;
import com.siparo.order.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryServiceStartTest {
    private final DeliveryAssignmentRepository assignments = mock(DeliveryAssignmentRepository.class);
    private final DeliveryService service = new DeliveryService(
            assignments, mock(CourierRepository.class), mock(OrderRepository.class),
            mock(CourierLocationRepository.class), mock(DeliveryEventRepository.class),
            mock(DeliveryPinService.class), mock(com.siparo.order.OrderMapper.class),
            mock(org.springframework.context.ApplicationEventPublisher.class));
    private final UUID courierId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    @Test
    void rejectsAssignmentThatIsNoLongerAccepted() {
        DeliveryAssignment assignment = assignment("CANCELLED", "PREPARING");
        assertThatThrownBy(() -> service.start(courierId, assignmentId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("DELIVERY_NOT_ACCEPTED"));
    }

    @Test
    void rejectsSecondActiveDelivery() {
        assignment("ACCEPTED", "PREPARING");
        when(assignments.existsByCourierIdAndStatus(courierId, "ON_THE_WAY")).thenReturn(true);
        assertThatThrownBy(() -> service.start(courierId, assignmentId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("COURIER_HAS_ACTIVE_DELIVERY"));
    }

    @Test
    void rejectsOrderThatIsNoLongerPreparing() {
        assignment("ACCEPTED", "CANCELLED");
        assertThatThrownBy(() -> service.start(courierId, assignmentId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("ORDER_NOT_READY_FOR_DELIVERY"));
    }

    private DeliveryAssignment assignment(String status, String orderStatus) {
        DeliveryAssignment assignment = mock(DeliveryAssignment.class);
        Order order = mock(Order.class);
        when(assignment.getStatus()).thenReturn(status);
        when(assignment.getOrder()).thenReturn(order);
        when(order.getStatus()).thenReturn(orderStatus);
        when(assignments.findByIdAndCourierId(assignmentId, courierId)).thenReturn(Optional.of(assignment));
        return assignment;
    }
}
