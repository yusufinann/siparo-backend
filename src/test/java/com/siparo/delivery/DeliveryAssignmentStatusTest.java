package com.siparo.delivery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryAssignmentStatusTest {
    @Test
    void terminalStatusesAreExplicit() {
        assertFalse(DeliveryAssignmentStatus.ASSIGNED.isTerminal());
        assertFalse(DeliveryAssignmentStatus.ACCEPTED.isTerminal());
        assertFalse(DeliveryAssignmentStatus.ON_THE_WAY.isTerminal());
        assertTrue(DeliveryAssignmentStatus.DELIVERED.isTerminal());
        assertTrue(DeliveryAssignmentStatus.REJECTED.isTerminal());
        assertTrue(DeliveryAssignmentStatus.FAILED.isTerminal());
        assertTrue(DeliveryAssignmentStatus.CANCELLED.isTerminal());
    }
}
