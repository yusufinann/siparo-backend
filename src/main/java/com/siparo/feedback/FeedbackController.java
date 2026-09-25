package com.siparo.feedback;

import com.siparo.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private static final String OWNER = "hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #restaurantId)";

    private final FeedbackService feedbackService;

    @PostMapping("/api/v1/customers/me/orders/{orderId}/review")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<FeedbackDtos.ReviewDto> review(Authentication authentication, @PathVariable UUID orderId,
                                                         @Valid @RequestBody FeedbackDtos.CreateReview request) {
        return ResponseEntity.ok(feedbackService.createReview(CurrentUser.id(authentication), orderId, request));
    }

    @PostMapping("/api/v1/customers/me/orders/{orderId}/issues")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<FeedbackDtos.IssueDto> reportIssue(Authentication authentication, @PathVariable UUID orderId,
                                                             @Valid @RequestBody FeedbackDtos.CreateIssue request) {
        return ResponseEntity.ok(feedbackService.reportIssue(CurrentUser.id(authentication), orderId, request));
    }

    @GetMapping("/api/v1/restaurants/{restaurantId}/reviews")
    @PreAuthorize(OWNER)
    public ResponseEntity<FeedbackDtos.ReviewSummary> reviews(@PathVariable UUID restaurantId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(feedbackService.reviewsForRestaurant(restaurantId, page, size));
    }

    @GetMapping("/api/v1/restaurants/{restaurantId}/issues")
    @PreAuthorize(OWNER)
    public ResponseEntity<List<FeedbackDtos.IssueDto>> issues(@PathVariable UUID restaurantId,
                                                              @RequestParam(required = false) String status) {
        return ResponseEntity.ok(feedbackService.issuesForRestaurant(restaurantId, status));
    }

    @PatchMapping("/api/v1/restaurants/{restaurantId}/issues/{issueId}/resolve")
    @PreAuthorize(OWNER)
    public ResponseEntity<FeedbackDtos.IssueDto> resolve(@PathVariable UUID restaurantId, @PathVariable UUID issueId,
                                                         @Valid @RequestBody(required = false) FeedbackDtos.ResolveIssue request) {
        return ResponseEntity.ok(feedbackService.resolveIssue(restaurantId, issueId, request));
    }
}
