package com.siparo.feedback;

import java.util.UUID;

public final class FeedbackEvents {
    private FeedbackEvents() {}

    public record IssueReported(UUID restaurantId, UUID orderId, UUID issueId) {}

    public record IssueResolved(UUID customerId, UUID orderId, UUID restaurantId, UUID issueId) {}
}
