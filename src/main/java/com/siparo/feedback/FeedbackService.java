package com.siparo.feedback;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.customer.Customer;
import com.siparo.order.Order;
import com.siparo.order.OrderMapper;
import com.siparo.order.OrderRepository;
import com.siparo.order.OrderStatus;
import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final ReviewRepository reviewRepository;
    private final OrderIssueRepository issueRepository;
    private final OrderRepository orderRepository;
    private final RestaurantService restaurantService;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- Değerlendirme ----------

    @Transactional
    public FeedbackDtos.ReviewDto createReview(UUID customerId, UUID orderId, FeedbackDtos.CreateReview request) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new BusinessException("REVIEW_ALREADY_EXISTS", "This order has already been reviewed");
        }
        if (!orderMapper.canReview(order)) {
            throw new BusinessException("REVIEW_NOT_ALLOWED", "Only recently delivered orders can be reviewed");
        }
        Review review = new Review();
        review.setOrderId(orderId);
        review.setRestaurantId(order.getRestaurant().getId());
        review.setCustomerId(customerId);
        review.setRating(request.rating().shortValue());
        review.setTags(request.tags() == null || request.tags().isEmpty() ? null : String.join(",", request.tags().stream().distinct().toList()));
        review.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        reviewRepository.saveAndFlush(review);
        refreshRestaurantRating(order.getRestaurant());
        return toDto(review, order, order.getCustomer());
    }

    /** Restoran puanı yalnızca gerçek değerlendirmelerin ortalamasıdır (1 ondalık). */
    private void refreshRestaurantRating(Restaurant restaurant) {
        Object[] row = reviewRepository.aggregate(restaurant.getId()).get(0);
        Double average = (Double) row[0];
        long count = (Long) row[1];
        restaurant.setRatingScore(average == null ? null : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP));
        restaurant.setRatingCount((int) count);
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.ReviewSummary reviewsForRestaurant(UUID restaurantId, int page, int size) {
        Restaurant restaurant = restaurantService.findOrThrow(restaurantId);
        Page<Review> reviews = reviewRepository.findAllByRestaurantIdOrderByCreatedAtDesc(restaurantId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50)));
        Map<UUID, Order> orders = orderRepository.findAllById(reviews.getContent().stream().map(Review::getOrderId).toList())
                .stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Integer, Long> distribution = new TreeMap<>();
        for (int star = 1; star <= 5; star++) distribution.put(star, 0L);
        for (Object[] row : reviewRepository.distribution(restaurantId)) {
            distribution.put(((Number) row[0]).intValue(), (Long) row[1]);
        }
        List<FeedbackDtos.ReviewDto> content = reviews.getContent().stream().map(review -> {
            Order order = orders.get(review.getOrderId());
            return toDto(review, order, order == null ? null : order.getCustomer());
        }).toList();
        return new FeedbackDtos.ReviewSummary(restaurant.getRatingScore(), restaurant.getRatingCount() == null ? 0 : restaurant.getRatingCount(),
                distribution, content, reviews.getNumber(), reviews.getTotalPages());
    }

    private FeedbackDtos.ReviewDto toDto(Review review, Order order, Customer customer) {
        return new FeedbackDtos.ReviewDto(review.getId(), review.getOrderId(), order == null ? null : order.getOrderNumber(),
                customer == null ? null : firstNameAndInitial(customer.getFullName()), review.getRating(),
                review.getTags() == null ? List.of() : Arrays.asList(review.getTags().split(",")),
                review.getComment(), review.getCreatedAt());
    }

    /** İşletme değerlendirmeyi siparişle eşleyebilir; müşterinin tam adı yerine ad + soyadın baş harfi gösterilir. */
    private String firstNameAndInitial(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;
        String[] parts = fullName.trim().split("\\s+");
        return parts.length < 2 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    // ---------- Sipariş sorunları ----------

    @Transactional
    public FeedbackDtos.IssueDto reportIssue(UUID customerId, UUID orderId, FeedbackDtos.CreateIssue request) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (OrderStatus.PENDING.name().equals(order.getStatus())) {
            // Onay bekleyen sipariş sorun bildirimi yerine iptal edilebilir.
            throw new BusinessException("ISSUE_NOT_ALLOWED", "Pending orders can be cancelled instead");
        }
        if (order.getCreatedAt().isBefore(LocalDateTime.now().minusDays(7))) {
            throw new BusinessException("ISSUE_NOT_ALLOWED", "Issues can be reported within 7 days");
        }
        if (issueRepository.existsByOrderIdAndStatus(orderId, "OPEN")) {
            throw new BusinessException("ISSUE_ALREADY_OPEN", "There is already an open issue for this order");
        }
        OrderIssue issue = new OrderIssue();
        issue.setOrderId(orderId);
        issue.setRestaurantId(order.getRestaurant().getId());
        issue.setCustomerId(customerId);
        issue.setType(request.type());
        issue.setDetail(request.detail() == null || request.detail().isBlank() ? null : request.detail().trim());
        issueRepository.save(issue);
        eventPublisher.publishEvent(new FeedbackEvents.IssueReported(order.getRestaurant().getId(), orderId, issue.getId()));
        return toDto(issue, order);
    }

    @Transactional(readOnly = true)
    public List<FeedbackDtos.IssueDto> issuesForRestaurant(UUID restaurantId, String status) {
        List<OrderIssue> issues = status == null || status.isBlank()
                ? issueRepository.findAllByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                : issueRepository.findAllByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status);
        Map<UUID, Order> orders = orderRepository.findAllById(issues.stream().map(OrderIssue::getOrderId).toList())
                .stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        return issues.stream().map(issue -> toDto(issue, orders.get(issue.getOrderId()))).toList();
    }

    @Transactional
    public FeedbackDtos.IssueDto resolveIssue(UUID restaurantId, UUID issueId, FeedbackDtos.ResolveIssue request) {
        OrderIssue issue = issueRepository.findByIdAndRestaurantId(issueId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("ISSUE_NOT_FOUND", "Issue not found"));
        issue.setStatus("RESOLVED");
        issue.setResolvedAt(LocalDateTime.now());
        issue.setResolutionNote(request == null || request.resolutionNote() == null || request.resolutionNote().isBlank()
                ? null : request.resolutionNote().trim());
        Order order = orderRepository.findById(issue.getOrderId()).orElse(null);
        eventPublisher.publishEvent(new FeedbackEvents.IssueResolved(issue.getCustomerId(), issue.getOrderId(), restaurantId, issueId));
        return toDto(issue, order);
    }

    private FeedbackDtos.IssueDto toDto(OrderIssue issue, Order order) {
        String phone = order == null ? null : order.getCustomer().getPhoneNumber();
        return new FeedbackDtos.IssueDto(issue.getId(), issue.getOrderId(), order == null ? null : order.getOrderNumber(),
                order == null ? null : order.getCustomer().getFullName(),
                phone != null && !phone.startsWith("deleted-") ? phone : null,
                issue.getType(), issue.getDetail(), issue.getStatus(), issue.getResolutionNote(), issue.getCreatedAt(), issue.getResolvedAt());
    }
}
