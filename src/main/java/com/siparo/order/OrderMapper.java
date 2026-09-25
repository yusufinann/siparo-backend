package com.siparo.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siparo.delivery.DeliveryAssignment;
import com.siparo.delivery.DeliveryAssignmentRepository;
import com.siparo.delivery.DeliveryAssignmentStatus;
import com.siparo.feedback.OrderIssue;
import com.siparo.feedback.OrderIssueRepository;
import com.siparo.feedback.Review;
import com.siparo.feedback.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Sipariş DTO eşlemesi; listelerde değerlendirme/sorun/kurye bilgileri toplu yüklenir (N+1 yok). */
@Component
@RequiredArgsConstructor
public class OrderMapper {

    /** Teslimattan sonra değerlendirme bu kadar gün açık kalır. */
    public static final int REVIEW_WINDOW_DAYS = 14;

    private final ObjectMapper objectMapper;
    private final ReviewRepository reviewRepository;
    private final OrderIssueRepository issueRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final OrderRepository orderRepository;

    public List<OrderDto> forCustomer(Collection<Order> orders) {
        List<UUID> ids = orders.stream().map(Order::getId).toList();
        Map<UUID, Review> reviews = ids.isEmpty() ? Map.of() : reviewRepository.findAllByOrderIdIn(ids).stream()
                .collect(Collectors.toMap(Review::getOrderId, Function.identity()));
        Map<UUID, OrderIssue> issues = latestIssues(ids);
        return orders.stream().map(order -> {
            OrderDto dto = base(order);
            Review review = reviews.get(order.getId());
            dto.setReviewRating(review == null ? null : (int) review.getRating());
            dto.setCanReview(review == null && canReview(order));
            dto.setCanCancel(OrderStatus.PENDING.name().equals(order.getStatus()));
            dto.setIssue(issueSummary(issues.get(order.getId())));
            return dto;
        }).toList();
    }

    public OrderDto forCustomer(Order order) {
        return forCustomer(List.of(order)).get(0);
    }

    public List<OrderDto> forRestaurant(Collection<Order> orders) {
        List<UUID> ids = orders.stream().map(Order::getId).toList();
        Map<UUID, DeliveryAssignment> assignments = ids.isEmpty() ? Map.of() : assignmentRepository.findAllByOrderIdIn(ids).stream()
                .collect(Collectors.toMap(assignment -> assignment.getOrder().getId(), Function.identity()));
        Map<UUID, OrderIssue> issues = latestIssues(ids);
        return orders.stream().map(order -> {
            OrderDto dto = base(order);
            dto.setCustomerPhone(order.getCustomer().getPhoneNumber() != null
                    && !order.getCustomer().getPhoneNumber().startsWith("deleted-") ? order.getCustomer().getPhoneNumber() : null);
            DeliveryAssignment assignment = assignments.get(order.getId());
            if (assignment != null) {
                OrderDto.DeliverySummary delivery = new OrderDto.DeliverySummary();
                delivery.setAssignmentId(assignment.getId());
                delivery.setCourierId(assignment.getCourier().getId());
                delivery.setCourierName(assignment.getCourier().getFullName());
                delivery.setStatus(assignment.getStatus());
                dto.setDelivery(delivery);
            }
            dto.setIssue(issueSummary(issues.get(order.getId())));
            // Aktif siparişlerde "ilk sipariş" rozeti: restoranın bu müşteriyle ilk teması (özenli hizmet fırsatı).
            if (OrderStatus.ACTIVE_NAMES.contains(order.getStatus())) {
                dto.setFirstOrder(orderRepository.countPreviousOrders(order.getCustomer().getId(), order.getRestaurant().getId(), order.getCreatedAt()) == 0);
            }
            return dto;
        }).toList();
    }

    public OrderDto forRestaurant(Order order) {
        OrderDto dto = forRestaurant(List.of(order)).get(0);
        dto.setFirstOrder(orderRepository.countPreviousOrders(order.getCustomer().getId(), order.getRestaurant().getId(), order.getCreatedAt()) == 0);
        return dto;
    }

    public boolean canReview(Order order) {
        if (!OrderStatus.DELIVERED.name().equals(order.getStatus())) return false;
        LocalDateTime deliveredAt = order.getStatusEvents().stream()
                .filter(event -> OrderStatus.DELIVERED.name().equals(event.getStatus()))
                .map(event -> LocalDateTime.ofInstant(event.getCreatedAt(), java.time.ZoneId.systemDefault()))
                .findFirst().orElse(order.getUpdatedAt());
        return deliveredAt == null || deliveredAt.isAfter(LocalDateTime.now().minusDays(REVIEW_WINDOW_DAYS));
    }

    private Map<UUID, OrderIssue> latestIssues(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return issueRepository.findAllByOrderIdIn(ids).stream()
                .collect(Collectors.toMap(OrderIssue::getOrderId, Function.identity(),
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
    }

    private OrderDto.IssueSummary issueSummary(OrderIssue issue) {
        if (issue == null) return null;
        OrderDto.IssueSummary summary = new OrderDto.IssueSummary();
        summary.setId(issue.getId());
        summary.setType(issue.getType());
        summary.setStatus(issue.getStatus());
        summary.setResolutionNote(issue.getResolutionNote());
        return summary;
    }

    private OrderDto base(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setRestaurantId(order.getRestaurant().getId());
        dto.setRestaurantName(order.getRestaurant().getName());
        dto.setRestaurantLogoUrl(order.getRestaurant().getLogoUrl());
        dto.setRestaurantPhone(order.getRestaurant().getPhone());
        dto.setRestaurantAddress(order.getRestaurant().getAddress());
        dto.setCustomerId(order.getCustomer().getId());
        dto.setCustomerName(order.getCustomer().getFullName());
        dto.setStatus(order.getStatus());
        dto.setFulfillmentType(order.getFulfillmentType());
        dto.setDeliveryAddress(order.getDeliveryAddress());
        dto.setDeliveryAddressDetail(order.getDeliveryAddressDetail());
        dto.setDeliveryLatitude(order.getDeliveryLatitude());
        dto.setDeliveryLongitude(order.getDeliveryLongitude());
        dto.setCancelReason(order.getCancelReason());
        dto.setSubtotal(order.getSubtotal());
        dto.setDeliveryFee(order.getDeliveryFee());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setCouponCode(order.getCouponCode());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setNote(order.getNote());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setEstimatedDeliveryAt(order.getEstimatedDeliveryAt());
        dto.setStatusHistory(order.getStatusEvents().stream()
                .sorted(Comparator.comparing(OrderStatusEvent::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(event -> {
                    OrderDto.OrderStatusEventDto eventDto = new OrderDto.OrderStatusEventDto();
                    eventDto.setStatus(event.getStatus());
                    eventDto.setReason(event.getReason());
                    eventDto.setCreatedAt(event.getCreatedAt());
                    return eventDto;
                }).toList());
        dto.setItems(items(order));
        return dto;
    }

    public List<OrderDto.OrderItemDto> items(Order order) {
        return order.getItems().stream().map(this::item).toList();
    }

    private OrderDto.OrderItemDto item(OrderItem item) {
        OrderDto.OrderItemDto dto = new OrderDto.OrderItemDto();
        dto.setId(item.getId());
        dto.setMenuItemId(item.getMenuItem().getId());
        dto.setMenuItemName(item.getMenuItemName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        dto.setNote(item.getNote());
        dto.setOptions(readOptions(item.getOptionsJson()));
        return dto;
    }

    public List<OrderDto.OptionSnapshot> readOptions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<OrderDto.OptionSnapshot>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    public String writeOptions(List<OrderDto.OptionSnapshot> options) {
        if (options == null || options.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize order options", exception);
        }
    }

    public static boolean isActiveAssignment(DeliveryAssignment assignment) {
        return assignment != null && !DeliveryAssignmentStatus.valueOf(assignment.getStatus()).isTerminal();
    }
}
