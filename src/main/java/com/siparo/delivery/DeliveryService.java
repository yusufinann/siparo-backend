package com.siparo.delivery;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.common.util.BusinessClock;
import com.siparo.order.Order;
import com.siparo.order.OrderMapper;
import com.siparo.order.OrderRepository;
import com.siparo.order.OrderService;
import com.siparo.order.OrderStatus;
import com.siparo.order.realtime.CourierLocationChangedEvent;
import com.siparo.order.realtime.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final long LOCATION_RATE_LIMIT_SECONDS = 3;
    private static final List<String> ACTIVE_STATUSES = List.of(
            DeliveryAssignmentStatus.ASSIGNED.name(), DeliveryAssignmentStatus.ACCEPTED.name(), DeliveryAssignmentStatus.ON_THE_WAY.name());

    private final DeliveryAssignmentRepository assignmentRepository;
    private final CourierRepository courierRepository;
    private final OrderRepository orderRepository;
    private final CourierLocationRepository locationRepository;
    private final DeliveryEventRepository eventRepository;
    private final DeliveryPinService pinService;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    public record CourierSummary(String status, boolean active, long activeDeliveries, long completedToday) {}

    @Transactional
    public DeliveryDto assign(UUID restaurantId, UUID orderId, UUID courierId, String actorId) {
        Order order = orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (order.isPickup()) {
            throw new BusinessException("ORDER_NOT_ASSIGNABLE", "Pickup orders do not need a courier");
        }
        if (!List.of(OrderStatus.PREPARING.name(), OrderStatus.CONFIRMED.name()).contains(order.getStatus())) {
            throw new BusinessException("ORDER_NOT_ASSIGNABLE", "Only preparing orders can be assigned to a courier");
        }
        Courier courier = courierRepository.findByIdAndRestaurantId(courierId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("COURIER_NOT_FOUND", "Courier not found"));
        if (!courier.isActive() || CourierStatus.OFFLINE.name().equals(courier.getStatus())) {
            throw new BusinessException("COURIER_NOT_AVAILABLE", "Courier is offline or inactive");
        }

        DeliveryAssignment assignment = assignmentRepository.findByOrderId(orderId).orElseGet(DeliveryAssignment::new);
        UUID previousCourierId = assignment.getCourier() == null ? null : assignment.getCourier().getId();
        if (assignment.getStatus() != null
                && List.of(DeliveryAssignmentStatus.ON_THE_WAY.name(), DeliveryAssignmentStatus.DELIVERED.name()).contains(assignment.getStatus())) {
            throw new BusinessException("COURIER_CHANGE_NOT_ALLOWED", "Courier cannot be changed after delivery has started");
        }
        String pin = pinService.generate();
        assignment.setOrder(order);
        assignment.setRestaurant(order.getRestaurant());
        assignment.setCourier(courier);
        assignment.setStatus(DeliveryAssignmentStatus.ASSIGNED.name());
        assignment.setDeliveryPinHash(pinService.hash(pin));
        assignment.setDeliveryPinEncrypted(pinService.encrypt(pin));
        assignment.setFailedPinAttempts(0);
        assignment.setPinLockedUntil(null);
        assignment.setFailureReason(null);
        assignment.setAssignedAt(Instant.now());
        assignment.setAcceptedAt(null);
        assignment.setStartedAt(null);
        assignment.setCompletedAt(null);
        assignment = assignmentRepository.save(assignment);
        event(assignment, "ASSIGNED", "RESTAURANT_ADMIN", actorId, null);

        if (previousCourierId != null && !previousCourierId.equals(courierId)) {
            publishCourierChange(previousCourierId, assignment, "REASSIGNED");
        }
        publishCourierChange(courierId, assignment, "ASSIGNED");
        return toDto(assignment);
    }

    @Transactional(readOnly = true)
    public List<DeliveryDto> listForRestaurant(UUID restaurantId, boolean activeOnly) {
        List<DeliveryAssignment> assignments = activeOnly
                ? assignmentRepository.findAllByRestaurantIdAndStatusIn(restaurantId, ACTIVE_STATUSES)
                : assignmentRepository.findAllByRestaurantIdOrderByAssignedAtDesc(restaurantId);
        return assignments.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryDto> listForCourier(UUID courierId, String scope) {
        return assignmentRepository.findAllByCourierIdOrderByAssignedAtDesc(courierId).stream()
                .filter(value -> "history".equalsIgnoreCase(scope)
                        ? DeliveryAssignmentStatus.valueOf(value.getStatus()).isTerminal()
                        : !DeliveryAssignmentStatus.valueOf(value.getStatus()).isTerminal())
                .limit("history".equalsIgnoreCase(scope) ? 50 : Long.MAX_VALUE)
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CourierSummary summary(UUID courierId) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("COURIER_NOT_FOUND", "Courier not found"));
        Instant startOfDay = BusinessClock.today().atStartOfDay(BusinessClock.ZONE).toInstant();
        return new CourierSummary(courier.getStatus(), courier.isActive(),
                assignmentRepository.countByCourierIdAndStatusIn(courierId, ACTIVE_STATUSES),
                assignmentRepository.countByCourierIdAndStatusAndCompletedAtGreaterThanEqual(
                        courierId, DeliveryAssignmentStatus.DELIVERED.name(), startOfDay));
    }

    @Transactional(readOnly = true)
    public DeliveryDto getForCourier(UUID courierId, UUID assignmentId) {
        return toDto(courierAssignment(courierId, assignmentId));
    }

    @Transactional
    public DeliveryDto accept(UUID courierId, UUID assignmentId) {
        DeliveryAssignment assignment = courierAssignment(courierId, assignmentId);
        requireStatus(assignment, DeliveryAssignmentStatus.ASSIGNED);
        assignment.setStatus(DeliveryAssignmentStatus.ACCEPTED.name());
        assignment.setAcceptedAt(Instant.now());
        event(assignment, "ACCEPTED", "COURIER", courierId.toString(), null);
        publishRestaurantRefresh(assignment);
        return toDto(assignment);
    }

    @Transactional
    public DeliveryDto reject(UUID courierId, UUID assignmentId, String reason) {
        DeliveryAssignment assignment = courierAssignment(courierId, assignmentId);
        requireStatus(assignment, DeliveryAssignmentStatus.ASSIGNED);
        assignment.setStatus(DeliveryAssignmentStatus.REJECTED.name());
        assignment.setFailureReason(reason);
        assignment.getCourier().setStatus(CourierStatus.AVAILABLE.name());
        event(assignment, "REJECTED", "COURIER", courierId.toString(), reason);
        publishRestaurantRefresh(assignment);
        return toDto(assignment);
    }

    @Transactional
    public DeliveryDto start(UUID courierId, UUID assignmentId) {
        DeliveryAssignment assignment = courierAssignment(courierId, assignmentId);
        if (!DeliveryAssignmentStatus.ACCEPTED.name().equals(assignment.getStatus())) {
            throw new BusinessException("DELIVERY_NOT_ACCEPTED", "Delivery action is not allowed from status " + assignment.getStatus());
        }
        if (assignmentRepository.existsByCourierIdAndStatus(courierId, DeliveryAssignmentStatus.ON_THE_WAY.name())) {
            throw new BusinessException("COURIER_HAS_ACTIVE_DELIVERY", "Courier already has an active delivery");
        }
        Order order = assignment.getOrder();
        if (!List.of(OrderStatus.PREPARING.name(), OrderStatus.CONFIRMED.name()).contains(order.getStatus())) {
            throw new BusinessException("ORDER_NOT_READY_FOR_DELIVERY", "Order cannot start delivery from its current status");
        }
        assignment.setStatus(DeliveryAssignmentStatus.ON_THE_WAY.name());
        assignment.setStartedAt(Instant.now());
        assignment.getCourier().setStatus(CourierStatus.BUSY.name());
        changeOrderStatus(order, OrderStatus.ON_THE_WAY);
        event(assignment, "STARTED", "COURIER", courierId.toString(), null);
        return toDto(assignment);
    }

    @Transactional
    public DeliveryDto updateLocation(UUID courierId, UUID assignmentId, DeliveryRequests.Location request) {
        DeliveryAssignment assignment = assignmentRepository.findForLocationUpdate(assignmentId, courierId)
                .orElseThrow(() -> new ResourceNotFoundException("DELIVERY_NOT_FOUND", "Delivery not found"));
        requireStatus(assignment, DeliveryAssignmentStatus.ON_THE_WAY);
        Instant now = Instant.now();
        if (request.recordedAt().isAfter(now.plus(2, ChronoUnit.MINUTES))
                || request.recordedAt().isBefore(now.minus(1, ChronoUnit.HOURS))) {
            throw new BusinessException("INVALID_LOCATION_TIMESTAMP", "Location timestamp is invalid");
        }
        if (assignment.getLastLocationAt() != null) {
            if (!request.recordedAt().isAfter(assignment.getLastLocationAt())) return toDto(assignment);
            if (Duration.between(assignment.getLastLocationAt(), request.recordedAt()).getSeconds() < LOCATION_RATE_LIMIT_SECONDS) {
                return toDto(assignment);
            }
        }
        assignment.setLastLatitude(request.latitude());
        assignment.setLastLongitude(request.longitude());
        assignment.setLastAccuracy(request.accuracy());
        assignment.setLastHeading(request.heading());
        assignment.setLastSpeed(request.speed());
        assignment.setLastLocationAt(request.recordedAt());

        CourierLocationPoint point = new CourierLocationPoint();
        point.setDeliveryAssignment(assignment);
        point.setCourier(assignment.getCourier());
        point.setLatitude(request.latitude());
        point.setLongitude(request.longitude());
        point.setAccuracy(request.accuracy());
        point.setHeading(request.heading());
        point.setSpeed(request.speed());
        point.setRecordedAt(request.recordedAt());
        locationRepository.save(point);
        eventPublisher.publishEvent(new CourierLocationChangedEvent(
                assignment.getOrder().getCustomer().getId(), assignment.getOrder().getId(),
                request.latitude(), request.longitude(), request.accuracy(), request.heading(),
                request.speed(), request.recordedAt()));
        return toDto(assignment);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public DeliveryDto complete(UUID courierId, UUID assignmentId, String pin) {
        DeliveryAssignment assignment = courierAssignment(courierId, assignmentId);
        requireStatus(assignment, DeliveryAssignmentStatus.ON_THE_WAY);
        Instant now = Instant.now();
        if (assignment.getPinLockedUntil() != null && assignment.getPinLockedUntil().isAfter(now)) {
            throw new BusinessException("DELIVERY_PIN_LOCKED", "Delivery PIN is temporarily locked", HttpStatus.TOO_MANY_REQUESTS,
                    Map.of("lockedUntil", assignment.getPinLockedUntil().toString()));
        }
        if (!pinService.matches(pin, assignment.getDeliveryPinHash())) {
            int attempts = assignment.getFailedPinAttempts() + 1;
            assignment.setFailedPinAttempts(attempts);
            if (attempts >= MAX_PIN_ATTEMPTS) {
                assignment.setPinLockedUntil(now.plus(15, ChronoUnit.MINUTES));
                assignment.setFailedPinAttempts(0);
            }
            event(assignment, "PIN_FAILED", "COURIER", courierId.toString(), null);
            throw new BusinessException("DELIVERY_PIN_INCORRECT", "Delivery PIN is incorrect",
                    Map.of("remainingAttempts", Math.max(0, MAX_PIN_ATTEMPTS - attempts)));
        }
        finishDelivery(assignment, "COURIER", courierId.toString(), null);
        return toDto(assignment);
    }

    @Transactional
    public DeliveryDto fail(UUID courierId, UUID assignmentId, String reason) {
        DeliveryAssignment assignment = courierAssignment(courierId, assignmentId);
        requireStatus(assignment, DeliveryAssignmentStatus.ON_THE_WAY);
        try {
            DeliveryFailureReason.valueOf(reason);
        } catch (Exception e) {
            throw new BusinessException("INVALID_FAILURE_REASON", "A valid delivery failure reason is required");
        }
        assignment.setStatus(DeliveryAssignmentStatus.FAILED.name());
        assignment.setFailureReason(reason);
        assignment.getCourier().setStatus(CourierStatus.AVAILABLE.name());
        changeOrderStatus(assignment.getOrder(), OrderStatus.PREPARING);
        event(assignment, "FAILED", "COURIER", courierId.toString(), reason);
        return toDto(assignment);
    }

    @Transactional
    public DeliveryDto completeOverride(UUID restaurantId, UUID assignmentId, String reason, String actorId) {
        DeliveryAssignment assignment = assignmentRepository.findById(assignmentId)
                .filter(value -> value.getRestaurant().getId().equals(restaurantId))
                .orElseThrow(() -> new ResourceNotFoundException("DELIVERY_NOT_FOUND", "Delivery not found"));
        if (!List.of(DeliveryAssignmentStatus.ON_THE_WAY.name(), DeliveryAssignmentStatus.FAILED.name()).contains(assignment.getStatus())) {
            throw new BusinessException("INVALID_DELIVERY_ACTION", "Delivery override is not allowed from status " + assignment.getStatus());
        }
        finishDelivery(assignment, "RESTAURANT_ADMIN", actorId, reason);
        return toDto(assignment);
    }

    @Transactional(readOnly = true)
    public TrackingDto tracking(UUID customerId, UUID orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));
        TrackingDto dto = new TrackingDto();
        dto.setEstimatedDeliveryAt(order.getEstimatedDeliveryAt());
        TrackingDto.Destination destination = new TrackingDto.Destination();
        destination.setLatitude(order.getDeliveryLatitude());
        destination.setLongitude(order.getDeliveryLongitude());
        dto.setDestination(destination);

        DeliveryAssignment assignment = assignmentRepository.findByOrderId(orderId).orElse(null);
        if (assignment == null || !DeliveryAssignmentStatus.ON_THE_WAY.name().equals(assignment.getStatus())) {
            dto.setTrackingAvailable(false);
            dto.setDeliveryStatus(assignment == null ? null : assignment.getStatus());
            return dto;
        }
        dto.setTrackingAvailable(true);
        dto.setDeliveryStatus(assignment.getStatus());
        TrackingDto.CourierSummary courier = new TrackingDto.CourierSummary();
        courier.setDisplayName(maskCourierName(assignment.getCourier().getFullName()));
        dto.setCourier(courier);
        dto.setLocation(locationDto(assignment));
        if (assignment.getLastLocationAt() == null) {
            dto.setLocationFreshness("waitingLocation");
        } else {
            long age = Duration.between(assignment.getLastLocationAt(), Instant.now()).getSeconds();
            dto.setLocationFreshness(age > 120 ? "unavailable" : age > 30 ? "updating" : "live");
        }
        dto.setDeliveryPin(pinService.decrypt(assignment.getDeliveryPinEncrypted()));
        return dto;
    }

    private void finishDelivery(DeliveryAssignment assignment, String actorType, String actorId, String reason) {
        assignment.setStatus(DeliveryAssignmentStatus.DELIVERED.name());
        assignment.setCompletedAt(Instant.now());
        assignment.getCourier().setStatus(CourierStatus.AVAILABLE.name());
        changeOrderStatus(assignment.getOrder(), OrderStatus.DELIVERED);
        event(assignment, "DELIVERED", actorType, actorId, reason);
    }

    private void changeOrderStatus(Order order, OrderStatus status) {
        order.setStatus(status.name());
        OrderService.addStatusEvent(order, status, null);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getRestaurant().getId(), order.getCustomer().getId(), order.getId(), status.name()));
    }

    /** Kuryenin kabul/ret kararı sipariş durumunu değiştirmez; işletme panosunun yenilenmesi için aynı olay kullanılır. */
    private void publishRestaurantRefresh(DeliveryAssignment assignment) {
        Order order = assignment.getOrder();
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getRestaurant().getId(), order.getCustomer().getId(), order.getId(), order.getStatus()));
    }

    private void publishCourierChange(UUID courierId, DeliveryAssignment assignment, String type) {
        eventPublisher.publishEvent(new DeliveryEvents.CourierDeliveryChanged(courierId, assignment.getId(),
                assignment.getOrder().getId(), type, assignment.getOrder().getOrderNumber(), assignment.getRestaurant().getName()));
    }

    private DeliveryAssignment courierAssignment(UUID courierId, UUID assignmentId) {
        return assignmentRepository.findByIdAndCourierId(assignmentId, courierId)
                .orElseThrow(() -> new ResourceNotFoundException("DELIVERY_NOT_FOUND", "Delivery not found"));
    }

    private void requireStatus(DeliveryAssignment assignment, DeliveryAssignmentStatus expected) {
        if (!expected.name().equals(assignment.getStatus())) {
            throw new BusinessException("INVALID_DELIVERY_ACTION", "Delivery action is not allowed from status " + assignment.getStatus(),
                    Map.of("status", assignment.getStatus()));
        }
    }

    private void event(DeliveryAssignment assignment, String type, String actorType, String actorId, String reason) {
        DeliveryEvent event = new DeliveryEvent();
        event.setDeliveryAssignment(assignment);
        event.setType(type);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setReason(reason);
        eventRepository.save(event);
    }

    private String maskCourierName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 2) return parts[0];
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private DeliveryDto.LocationDto locationDto(DeliveryAssignment assignment) {
        if (assignment.getLastLatitude() == null || assignment.getLastLongitude() == null) return null;
        DeliveryDto.LocationDto dto = new DeliveryDto.LocationDto();
        dto.setLatitude(assignment.getLastLatitude());
        dto.setLongitude(assignment.getLastLongitude());
        dto.setAccuracy(assignment.getLastAccuracy());
        dto.setHeading(assignment.getLastHeading());
        dto.setSpeed(assignment.getLastSpeed());
        dto.setRecordedAt(assignment.getLastLocationAt());
        return dto;
    }

    private DeliveryDto toDto(DeliveryAssignment assignment) {
        Order order = assignment.getOrder();
        DeliveryDto dto = new DeliveryDto();
        dto.setId(assignment.getId());
        dto.setOrderId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setRestaurantId(assignment.getRestaurant().getId());
        dto.setRestaurantName(assignment.getRestaurant().getName());
        dto.setRestaurantPhone(assignment.getRestaurant().getPhone());
        dto.setRestaurantAddress(assignment.getRestaurant().getAddress());
        dto.setRestaurantLatitude(assignment.getRestaurant().getLatitude());
        dto.setRestaurantLongitude(assignment.getRestaurant().getLongitude());
        dto.setCourierId(assignment.getCourier().getId());
        dto.setCourierName(assignment.getCourier().getFullName());
        dto.setCourierPhone(assignment.getCourier().getPhoneNumber());
        dto.setStatus(assignment.getStatus());
        dto.setOrderStatus(order.getStatus());
        dto.setCustomerName(order.getCustomer().getFullName());
        String phone = order.getCustomer().getPhoneNumber();
        dto.setCustomerPhone(phone != null && !phone.startsWith("deleted-") ? phone : null);
        dto.setDeliveryAddress(order.getDeliveryAddress());
        dto.setDeliveryAddressDetail(order.getDeliveryAddressDetail());
        dto.setDestinationLatitude(order.getDeliveryLatitude());
        dto.setDestinationLongitude(order.getDeliveryLongitude());
        dto.setOrderNote(order.getNote());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setOrderTotal(order.getTotalAmount());
        dto.setAmountToCollect("ONLINE_CARD".equals(order.getPaymentMethod()) ? BigDecimal.ZERO : order.getTotalAmount());
        dto.setItems(orderMapper.items(order));
        dto.setEstimatedDeliveryAt(order.getEstimatedDeliveryAt());
        dto.setFailureReason(assignment.getFailureReason());
        dto.setAssignedAt(assignment.getAssignedAt());
        dto.setAcceptedAt(assignment.getAcceptedAt());
        dto.setStartedAt(assignment.getStartedAt());
        dto.setCompletedAt(assignment.getCompletedAt());
        dto.setLastLocation(locationDto(assignment));
        return dto;
    }
}
