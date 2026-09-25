package com.siparo.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FeedbackDtos {
    private FeedbackDtos() {}

    public static final List<String> REVIEW_TAGS = List.of("TASTY", "FAST_DELIVERY", "HOT", "GOOD_PORTION", "FRIENDLY_COURIER", "WELL_PACKED");

    public record CreateReview(
            @NotNull @Min(1) @Max(5) Integer rating,
            List<@Pattern(regexp = "TASTY|FAST_DELIVERY|HOT|GOOD_PORTION|FRIENDLY_COURIER|WELL_PACKED") String> tags,
            @Size(max = 1000) String comment) {}

    public record ReviewDto(UUID id, UUID orderId, Long orderNumber, String customerName, int rating, List<String> tags,
                            String comment, LocalDateTime createdAt) {}

    public record ReviewSummary(BigDecimal ratingScore, int ratingCount, Map<Integer, Long> distribution, List<ReviewDto> reviews,
                                int page, int totalPages) {}

    public record CreateIssue(
            @NotBlank @Pattern(regexp = "MISSING_ITEM|WRONG_ITEM|LATE_DELIVERY|COLD_FOOD|QUALITY|OTHER") String type,
            @Size(max = 1000) String detail) {}

    public record ResolveIssue(@Size(max = 500) String resolutionNote) {}

    public record IssueDto(UUID id, UUID orderId, Long orderNumber, String customerName, String customerPhone, String type,
                           String detail, String status, String resolutionNote, LocalDateTime createdAt, LocalDateTime resolvedAt) {}
}
