package com.siparo.order;

import com.siparo.cart.Cart;
import com.siparo.cart.CartService;
import com.siparo.common.PageDto;
import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.common.util.BusinessClock;
import com.siparo.common.util.GeoUtil;
import com.siparo.coupon.CouponService;
import com.siparo.customer.Customer;
import com.siparo.customer.CustomerAddress;
import com.siparo.customer.CustomerService;
import com.siparo.delivery.CourierStatus;
import com.siparo.delivery.DeliveryAssignment;
import com.siparo.delivery.DeliveryAssignmentRepository;
import com.siparo.delivery.DeliveryAssignmentStatus;
import com.siparo.delivery.DeliveryEvent;
import com.siparo.delivery.DeliveryEventRepository;
import com.siparo.delivery.DeliveryEvents;
import com.siparo.order.realtime.OrderCreatedEvent;
import com.siparo.order.realtime.OrderStatusChangedEvent;
import com.siparo.platform.PlatformFeatures;
import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final RestaurantService restaurantService;
    private final CartService cartService;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final OrderMapper orderMapper;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final PlatformFeatures platformFeatures;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- Müşteri: sipariş oluşturma ----------

    @Transactional
    public OrderDto createOrder(UUID customerId, CreateOrderRequest request) {
        Customer customer = customerService.findActive(customerId);
        Cart cart = cartService.getCartOrThrow(customerId);
        if (cart.getRestaurant() == null || cart.getItems().isEmpty()) {
            throw new BusinessException("CART_EMPTY", "Cart is empty");
        }
        Restaurant restaurant = cart.getRestaurant();
        restaurantService.requireOpen(restaurant);

        boolean pickup = "PICKUP".equals(request.getFulfillmentType());
        if (pickup && !restaurant.isPickupEnabled()) {
            throw new BusinessException("PICKUP_NOT_AVAILABLE", "Restaurant does not offer pickup");
        }
        if (!pickup && !restaurant.isDeliveryEnabled()) {
            throw new BusinessException("DELIVERY_NOT_AVAILABLE", "Restaurant does not offer delivery");
        }
        if (request.getPaymentMethod() == PaymentMethod.ONLINE_CARD && !platformFeatures.onlinePaymentEnabled()) {
            throw new BusinessException("PAYMENT_METHOD_UNAVAILABLE", "Online payment is not available");
        }

        Order order = new Order();
        order.setOrderNumber(orderRepository.nextOrderNumber());
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setFulfillmentType(pickup ? "PICKUP" : "DELIVERY");

        if (!pickup) {
            if (request.getAddressId() == null) throw new BusinessException("ADDRESS_REQUIRED", "Delivery address is required");
            CustomerAddress address = customerService.ownedAddress(customerId, request.getAddressId());
            requireWithinDeliveryArea(restaurant, address);
            // Adres anlık kopyalanır: müşteri sonradan adresi silse/değiştirse de sipariş bozulmaz.
            order.setDeliveryAddress(address.getAddress());
            order.setDeliveryAddressDetail(address.getAddressDetail());
            order.setDeliveryLatitude(address.getLatitude());
            order.setDeliveryLongitude(address.getLongitude());
        }

        List<CartService.CartLine> lines = cartService.resolveLines(cart);
        List<String> unavailable = lines.stream().filter(line -> !line.available())
                .map(line -> line.entity().getMenuItem().getName()).toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessException("MENU_ITEM_UNAVAILABLE", "Some items are unavailable", Map.of("items", unavailable));
        }

        PricingService.Quote quote = pricingService.quote(restaurant, lines.stream().map(CartService.CartLine::resolved).toList(),
                cart.getCoupon(), customerId, !pickup);
        if (quote.coupon() != null && !quote.coupon().valid()) {
            throw new BusinessException(quote.coupon().errorCode(), "Coupon cannot be applied", quote.coupon().params());
        }
        if (quote.minOrderRemaining().signum() > 0) {
            throw new BusinessException("BELOW_MIN_ORDER", "Order total is below the minimum",
                    Map.of("amount", restaurant.getMinOrderAmount()));
        }
        if (request.getExpectedTotal() != null && request.getExpectedTotal().compareTo(quote.total()) != 0) {
            throw new BusinessException("PRICE_CHANGED", "Order total has changed", HttpStatus.CONFLICT, Map.of("total", quote.total()));
        }

        for (CartService.CartLine line : lines) {
            PricingService.ResolvedLine resolved = line.resolved();
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setMenuItem(resolved.item());
            item.setMenuItemName(resolved.item().getName());
            item.setQuantity(resolved.quantity());
            item.setUnitPrice(resolved.unitPrice());
            item.setOptionsTotal(resolved.optionsTotal());
            item.setNote(resolved.note());
            item.setOptionsJson(orderMapper.writeOptions(resolved.options().stream().map(option -> {
                OrderDto.OptionSnapshot snapshot = new OrderDto.OptionSnapshot();
                snapshot.setId(option.getId());
                snapshot.setGroupName(option.getGroup().getName());
                snapshot.setName(option.getName());
                snapshot.setPriceDelta(option.getPriceDelta());
                return snapshot;
            }).toList()));
            order.getItems().add(item);
        }

        order.setSubtotal(quote.subtotal());
        order.setDeliveryFee(quote.deliveryFee());
        order.setDiscountAmount(quote.discount());
        order.setTotalAmount(quote.total());
        order.setPaymentMethod(request.getPaymentMethod().name());
        order.setNote(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim());
        order.setStatus(OrderStatus.PENDING.name());
        addStatusEvent(order, OrderStatus.PENDING, null);
        if (quote.coupon() != null) {
            order.setCouponId(quote.coupon().coupon().getId());
            order.setCouponCode(quote.coupon().coupon().getCode());
        }

        Order saved = orderRepository.save(order);
        if (quote.coupon() != null) {
            couponService.recordRedemption(quote.coupon().coupon(), customerId, saved.getId(), quote.discount());
        }
        cartService.clearCart(customerId);
        eventPublisher.publishEvent(new OrderCreatedEvent(restaurant.getId(), saved.getId()));
        return orderMapper.forCustomer(saved);
    }

    private void requireWithinDeliveryArea(Restaurant restaurant, CustomerAddress address) {
        if (restaurant.getDeliveryRadiusKm() == null) return;
        Double distance = GeoUtil.distanceKm(restaurant.getLatitude(), restaurant.getLongitude(), address.getLatitude(), address.getLongitude());
        if (distance != null && distance > restaurant.getDeliveryRadiusKm().doubleValue()) {
            throw new BusinessException("OUT_OF_DELIVERY_AREA", "Address is outside the delivery area",
                    Map.of("distanceKm", Math.round(distance * 10.0) / 10.0, "radiusKm", restaurant.getDeliveryRadiusKm()));
        }
    }

    // ---------- Müşteri: okuma / iptal ----------

    @Transactional(readOnly = true)
    public List<OrderDto> getCustomerOrders(UUID customerId) {
        return orderMapper.forCustomer(orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId));
    }

    @Transactional(readOnly = true)
    public OrderDto getCustomerOrder(UUID customerId, UUID orderId) {
        return orderMapper.forCustomer(customerOrder(customerId, orderId));
    }

    public Order customerOrder(UUID customerId, UUID orderId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }

    /** Müşteri yalnızca restoran onaylamadan (PENDING) iptal edebilir. */
    @Transactional
    public OrderDto cancelOrderAsCustomer(UUID customerId, UUID orderId) {
        Order order = customerOrder(customerId, orderId);
        if (toOrderStatus(order.getStatus()) != OrderStatus.PENDING) {
            throw new BusinessException("ORDER_NOT_CANCELLABLE", "Only pending orders can be cancelled by the customer");
        }
        cancel(order, OrderCancelReason.CUSTOMER_CANCELLED.name(), "CUSTOMER", customerId.toString());
        return orderMapper.forCustomer(orderRepository.save(order));
    }

    // ---------- İşletme ----------

    /** Canlı operasyon panosu: aktif siparişler + bugünün tamamlananları. */
    @Transactional(readOnly = true)
    public List<OrderDto> getLiveOrders(UUID restaurantId) {
        return orderMapper.forRestaurant(orderRepository.findLive(restaurantId, OrderStatus.ACTIVE_NAMES,
                BusinessClock.startOfBusinessDay(BusinessClock.today())));
    }

    /** Sipariş geçmişi: durum, tarih aralığı ve arama (sipariş no / müşteri adı) ile sayfalı. */
    @Transactional(readOnly = true)
    public PageDto<OrderDto> searchOrders(UUID restaurantId, List<String> statuses, LocalDate from, LocalDate to,
                                          String query, int page, int size) {
        Specification<Order> spec = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("restaurant").get("id"), restaurantId));
            if (statuses != null && !statuses.isEmpty()) predicates.add(root.get("status").in(statuses));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), BusinessClock.startOfBusinessDay(from)));
            if (to != null) predicates.add(builder.lessThan(root.get("createdAt"), BusinessClock.startOfBusinessDay(to.plusDays(1))));
            if (query != null && !query.isBlank()) {
                String term = query.trim().replace("#", "").replaceAll("(?i)^sp-", "");
                List<Predicate> any = new ArrayList<>();
                any.add(builder.like(builder.lower(root.get("customer").get("fullName")), "%" + term.toLowerCase() + "%"));
                if (term.matches("\\d{1,18}")) any.add(builder.equal(root.get("orderNumber"), Long.parseLong(term)));
                predicates.add(builder.or(any.toArray(Predicate[]::new)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<Order> result = orderRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageDto.of(result, orderMapper::forRestaurant);
    }

    @Transactional(readOnly = true)
    public OrderDto getRestaurantOrder(UUID restaurantId, UUID orderId) {
        return orderMapper.forRestaurant(restaurantOrder(restaurantId, orderId));
    }

    private Order restaurantOrder(UUID restaurantId, UUID orderId) {
        return orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }

    /**
     * İşletmenin durum değişikliği. Kurye atanmış teslimatlarda yol/teslim adımlarını kurye yönetir;
     * kuryesiz (işletmenin kendi yönettiği) teslimatlarda işletme de ilerletebilir.
     */
    @Transactional
    public OrderDto updateOrderStatus(UUID restaurantId, UUID orderId, String newStatus, String reason, Integer prepMinutes) {
        Order order = restaurantOrder(restaurantId, orderId);
        OrderStatus current = toOrderStatus(order.getStatus());
        OrderStatus next = toOrderStatus(newStatus);
        if (!current.canTransitionTo(next)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Order status cannot change from " + current + " to " + next,
                    Map.of("from", current.name(), "to", next.name()));
        }
        if (next == OrderStatus.READY_FOR_PICKUP && !order.isPickup()) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Only pickup orders can be marked ready");
        }
        if (next == OrderStatus.ON_THE_WAY && order.isPickup()) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Pickup orders are not delivered");
        }
        if ((next == OrderStatus.ON_THE_WAY || (next == OrderStatus.DELIVERED && !order.isPickup()))
                && OrderMapper.isActiveAssignment(deliveryAssignmentRepository.findByOrderId(orderId).orElse(null))) {
            throw new BusinessException("DELIVERY_STATUS_BY_COURIER", "Delivery status is managed by the assigned courier");
        }

        if (next == OrderStatus.CANCELLED) {
            cancel(order, toCancelReason(reason).name(), "RESTAURANT_ADMIN", restaurantId.toString());
        } else {
            if (current.ordinal() <= OrderStatus.CONFIRMED.ordinal() && next == OrderStatus.PREPARING) {
                validatePrepMinutes(prepMinutes);
                order.setEstimatedDeliveryAt(Instant.now().plus(prepMinutes + deliveryMinutes(order), ChronoUnit.MINUTES));
            }
            order.setStatus(next.name());
            addStatusEvent(order, next, null);
            publishStatus(order, next);
        }
        return orderMapper.forRestaurant(orderRepository.save(order));
    }

    /** Onaydaki süre hazırlık süresidir; teslimat siparişlerinde restoranın teslimat süresinin üst sınırı eklenir. */
    private int deliveryMinutes(Order order) {
        if (order.isPickup()) return 0;
        Integer max = order.getRestaurant().getDeliveryTimeMax();
        return max == null ? 0 : Math.min(max, 120);
    }

    private void cancel(Order order, String reason, String actorType, String actorId) {
        order.setStatus(OrderStatus.CANCELLED.name());
        order.setCancelReason(reason);
        cancelDeliveryAssignment(order.getId(), actorType, actorId, reason);
        if (order.getCouponId() != null) couponService.releaseRedemption(order.getId());
        addStatusEvent(order, OrderStatus.CANCELLED, reason);
        publishStatus(order, OrderStatus.CANCELLED);
    }

    private void publishStatus(Order order, OrderStatus status) {
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getRestaurant().getId(), order.getCustomer().getId(), order.getId(), status.name()));
    }

    private void validatePrepMinutes(Integer prepMinutes) {
        if (prepMinutes == null || prepMinutes < 5 || prepMinutes > 180) {
            throw new BusinessException("INVALID_PREP_TIME", "Preparation time must be between 5 and 180 minutes");
        }
    }

    public static void addStatusEvent(Order order, OrderStatus status, String reason) {
        OrderStatusEvent event = new OrderStatusEvent();
        event.setOrder(order);
        event.setStatus(status.name());
        event.setReason(reason);
        order.getStatusEvents().add(event);
    }

    private void cancelDeliveryAssignment(UUID orderId, String actorType, String actorId, String reason) {
        deliveryAssignmentRepository.findByOrderId(orderId).ifPresent(assignment -> {
            if (!DeliveryAssignmentStatus.valueOf(assignment.getStatus()).isTerminal()) {
                assignment.setStatus(DeliveryAssignmentStatus.CANCELLED.name());
                if (CourierStatus.BUSY.name().equals(assignment.getCourier().getStatus())) {
                    assignment.getCourier().setStatus(CourierStatus.AVAILABLE.name());
                }
                DeliveryEvent event = new DeliveryEvent();
                event.setDeliveryAssignment(assignment);
                event.setType("CANCELLED");
                event.setActorType(actorType);
                event.setActorId(actorId);
                event.setReason(reason);
                deliveryEventRepository.save(event);
                eventPublisher.publishEvent(new DeliveryEvents.CourierDeliveryChanged(assignment.getCourier().getId(),
                        assignment.getId(), orderId, "CANCELLED", assignment.getOrder().getOrderNumber(),
                        assignment.getRestaurant().getName()));
            }
        });
    }

    private OrderStatus toOrderStatus(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("UNKNOWN_ORDER_STATUS", "Unknown order status: " + status);
        }
    }

    private OrderCancelReason toCancelReason(String reason) {
        try {
            OrderCancelReason value = OrderCancelReason.valueOf(reason);
            if (value == OrderCancelReason.CUSTOMER_CANCELLED) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("INVALID_CANCEL_REASON", "A valid cancel reason is required");
        }
    }

    /** Teslimat zaman aşımı gibi durumlar için dışarıdan erişim (DeliveryService). */
    public static boolean hasActiveAssignment(DeliveryAssignment assignment) {
        return OrderMapper.isActiveAssignment(assignment);
    }
}
