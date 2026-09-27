package com.siparo.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siparo.coupon.Coupon;
import com.siparo.coupon.CouponRepository;
import com.siparo.coupon.CouponService;
import com.siparo.customer.Customer;
import com.siparo.customer.CustomerRepository;
import com.siparo.customer.CustomerRestaurant;
import com.siparo.customer.CustomerRestaurantRepository;
import com.siparo.delivery.DeliveryEvents;
import com.siparo.feedback.FeedbackEvents;
import com.siparo.order.Order;
import com.siparo.order.OrderRepository;
import com.siparo.order.realtime.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bildirim merkezi kayıtları ve push gönderimi. Uygulama içi kayıt her zaman oluşturulur; push yalnızca sağlayıcı
 * yapılandırılmışsa ve müşteri ilgili tercihi açık bıraktıysa gönderilir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Map<String, String> STATUS_TYPES = Map.of(
            "PREPARING", "ORDER_ACCEPTED",
            "READY_FOR_PICKUP", "ORDER_READY",
            "ON_THE_WAY", "ORDER_ON_THE_WAY",
            "DELIVERED", "ORDER_DELIVERED",
            "CANCELLED", "ORDER_CANCELLED");

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final CustomerRepository customerRepository;
    private final CustomerRestaurantRepository customerRestaurantRepository;
    private final OrderRepository orderRepository;
    private final CouponRepository couponRepository;
    private final PushSender pushSender;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public record NotificationDto(UUID id, String type, UUID orderId, UUID restaurantId, Map<String, Object> params,
                                  boolean read, LocalDateTime createdAt) {}

    /** Müşteriye push'la aynı koşulda gönderilen anlık bildirim; açık uygulama bunu toast + ses olarak gösterir. */
    public record NotificationCreatedEvent(UUID customerId, NotificationDto notification) {}

    public record RegisterDevice(String token, String platform, String locale) {}

    // ---------- Olay dinleyicileri ----------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        String type = STATUS_TYPES.get(event.status());
        if (type == null) return;
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) return;
        // Müşterinin kendi iptali için bildirim gerekmez.
        if ("CANCELLED".equals(event.status()) && "CUSTOMER_CANCELLED".equals(order.getCancelReason())) return;
        Map<String, Object> params = new HashMap<>();
        params.put("restaurantName", order.getRestaurant().getName());
        params.put("orderNumber", order.getOrderNumber());
        if (order.getCancelReason() != null) params.put("cancelReason", order.getCancelReason());
        Customer customer = customerRepository.findById(event.customerId()).orElse(null);
        if (customer == null || customer.isDeleted()) return;
        Notification notification = create(customer.getId(), type, order.getId(), order.getRestaurant().getId(), params);
        if (customer.isNotifyOrderUpdates()) {
            notifyCustomer(notification, params, Map.of("orderId", order.getId().toString(), "type", type));
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCouponPublished(CouponService.CouponPublishedEvent event) {
        Coupon coupon = couponRepository.findById(event.couponId()).orElse(null);
        if (coupon == null) return;
        Map<String, Object> params = new HashMap<>();
        params.put("restaurantName", coupon.getRestaurant().getName());
        params.put("title", coupon.getTitle());
        params.put("code", coupon.getCode());
        for (CustomerRestaurant link : customerRestaurantRepository.findAllById_RestaurantIdOrderByCreatedAtDesc(event.restaurantId())) {
            if (link.getRemovedAt() != null) continue;
            Customer customer = customerRepository.findById(link.getId().getCustomerId()).orElse(null);
            if (customer == null || customer.isDeleted() || !customer.isNotifyCampaigns()) continue;
            Notification notification = create(customer.getId(), "CAMPAIGN", null, event.restaurantId(), params);
            notifyCustomer(notification, params, Map.of("type", "CAMPAIGN", "restaurantId", event.restaurantId().toString()));
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onIssueResolved(FeedbackEvents.IssueResolved event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) return;
        Map<String, Object> params = Map.of("restaurantName", order.getRestaurant().getName(), "orderNumber", order.getOrderNumber());
        Notification notification = create(event.customerId(), "ISSUE_RESOLVED", order.getId(), event.restaurantId(), params);
        notifyCustomer(notification, params, Map.of("orderId", order.getId().toString(), "type", "ISSUE_RESOLVED"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCourierDeliveryChanged(DeliveryEvents.CourierDeliveryChanged event) {
        if (!"ASSIGNED".equals(event.type()) && !"CANCELLED".equals(event.type())) return;
        String type = "ASSIGNED".equals(event.type()) ? "DELIVERY_ASSIGNED" : "DELIVERY_CANCELLED";
        Map<String, Object> params = new HashMap<>();
        params.put("restaurantName", event.restaurantName());
        params.put("orderNumber", event.orderNumber());
        push("COURIER", event.courierId(), type, params, Map.of("assignmentId", event.assignmentId().toString(), "type", type));
    }

    // ---------- Müşteri uçları ----------

    @Transactional(readOnly = true)
    public List<NotificationDto> list(UUID customerId) {
        return notificationRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(0, 100)).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID customerId) {
        return notificationRepository.countByCustomerIdAndReadAtIsNull(customerId);
    }

    @Transactional
    public void markRead(UUID customerId, UUID notificationId) {
        notificationRepository.findByIdAndCustomerId(notificationId, customerId)
                .ifPresent(notification -> {
                    if (notification.getReadAt() == null) notification.setReadAt(LocalDateTime.now());
                });
    }

    @Transactional
    public void markAllRead(UUID customerId) {
        notificationRepository.markAllRead(customerId, LocalDateTime.now());
    }

    @Transactional
    public void registerDevice(String ownerType, UUID ownerId, RegisterDevice request) {
        if (request == null || request.token() == null || request.token().isBlank()) return;
        DeviceToken token = deviceTokenRepository.findByToken(request.token().trim()).orElseGet(DeviceToken::new);
        token.setToken(request.token().trim());
        token.setOwnerType(ownerType);
        token.setOwnerId(ownerId);
        token.setPlatform(request.platform());
        token.setLocale(request.locale() != null && request.locale().toLowerCase().startsWith("en") ? "en" : "tr");
        token.setLastSeenAt(LocalDateTime.now());
        deviceTokenRepository.save(token);
    }

    @Transactional
    public void unregisterDevice(String ownerType, UUID ownerId, String token) {
        deviceTokenRepository.deleteOwnedToken(token, ownerType, ownerId);
    }

    // ---------- Yardımcılar ----------

    private Notification create(UUID customerId, String type, UUID orderId, UUID restaurantId, Map<String, Object> params) {
        Notification notification = new Notification();
        notification.setCustomerId(customerId);
        notification.setType(type);
        notification.setOrderId(orderId);
        notification.setRestaurantId(restaurantId);
        try {
            notification.setParams(objectMapper.writeValueAsString(params));
        } catch (Exception exception) {
            notification.setParams(null);
        }
        return notificationRepository.save(notification);
    }

    /** Açık uygulamaya anlık olay (commit sonrası websocket) ve kapalı uygulamaya push. İkisi aynı tercih koşuluyla çağrılır. */
    private void notifyCustomer(Notification notification, Map<String, Object> params, Map<String, String> data) {
        eventPublisher.publishEvent(new NotificationCreatedEvent(notification.getCustomerId(), toDto(notification)));
        push("CUSTOMER", notification.getCustomerId(), notification.getType(), params, data);
    }

    private void push(String ownerType, UUID ownerId, String type, Map<String, Object> params, Map<String, String> data) {
        if (!pushSender.isEnabled()) return;
        List<DeviceToken> tokens = deviceTokenRepository.findAllByOwnerTypeAndOwnerId(ownerType, ownerId);
        if (tokens.isEmpty()) return;
        Map<String, List<String>> byLocale = new HashMap<>();
        tokens.forEach(token -> byLocale.computeIfAbsent(token.getLocale() == null ? "tr" : token.getLocale(), key -> new java.util.ArrayList<>())
                .add(token.getToken()));
        byLocale.forEach((locale, list) -> {
            String[] text = PushTexts.render(type, params, locale);
            pushSender.send(new PushSender.PushMessage(list, text[0], text[1], data));
        });
    }

    private NotificationDto toDto(Notification notification) {
        Map<String, Object> params = Map.of();
        if (notification.getParams() != null) {
            try {
                params = objectMapper.readValue(notification.getParams(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception exception) {
                log.debug("Could not parse notification params {}", notification.getId());
            }
        }
        return new NotificationDto(notification.getId(), notification.getType(), notification.getOrderId(),
                notification.getRestaurantId(), params, notification.getReadAt() != null, notification.getCreatedAt());
    }
}
