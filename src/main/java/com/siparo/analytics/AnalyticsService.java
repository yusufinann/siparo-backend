package com.siparo.analytics;

import com.siparo.common.PageDto;
import com.siparo.common.util.BusinessClock;
import com.siparo.customer.Customer;
import com.siparo.customer.CustomerRepository;
import com.siparo.customer.CustomerRestaurant;
import com.siparo.customer.CustomerRestaurantRepository;
import com.siparo.delivery.Courier;
import com.siparo.delivery.CourierRepository;
import com.siparo.delivery.CourierStatus;
import com.siparo.delivery.DeliveryAssignment;
import com.siparo.delivery.DeliveryAssignmentRepository;
import com.siparo.feedback.OrderIssueRepository;
import com.siparo.menu.MenuItemRepository;
import com.siparo.order.Order;
import com.siparo.order.OrderItem;
import com.siparo.order.OrderMapper;
import com.siparo.order.OrderRepository;
import com.siparo.order.OrderStatus;
import com.siparo.order.OrderStatusEvent;
import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantDto;
import com.siparo.restaurant.RestaurantMapper;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** İşletme paneli metrikleri. Tümü veritabanındaki gerçek kayıtlardan hesaplanır; tahmin yalnızca açıkça etiketlenir. */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final long ACCEPT_WARNING_MINUTES = 5;
    private static final int WINDOW_DAYS = 30;

    private final OrderRepository orderRepository;
    private final RestaurantService restaurantService;
    private final RestaurantMapper restaurantMapper;
    private final CustomerRestaurantRepository customerRestaurantRepository;
    private final CustomerRepository customerRepository;
    private final CourierRepository courierRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final OrderIssueRepository issueRepository;
    private final MenuItemRepository menuItemRepository;

    @Transactional(readOnly = true)
    public AnalyticsDtos.Overview overview(UUID restaurantId) {
        Restaurant restaurant = restaurantService.findOrThrow(restaurantId);
        LocalDate today = BusinessClock.today();
        LocalDateTime startOfToday = BusinessClock.startOfBusinessDay(today);
        LocalDateTime windowStart = BusinessClock.startOfBusinessDay(today.minusDays(WINDOW_DAYS - 1));

        List<Order> recent = orderRepository.findAllByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, windowStart);
        List<Order> live = orderRepository.findLive(restaurantId, OrderStatus.ACTIVE_NAMES, startOfToday);

        return new AnalyticsDtos.Overview(
                today(recent, live, startOfToday),
                problems(restaurant, live),
                direct(restaurant, recent, windowStart),
                new AnalyticsDtos.Rating(restaurant.getRatingScore(), restaurant.getRatingCount() == null ? 0 : restaurant.getRatingCount()),
                topItems(recent));
    }

    private AnalyticsDtos.Today today(List<Order> recent, List<Order> live, LocalDateTime startOfToday) {
        List<Order> todays = recent.stream().filter(order -> !order.getCreatedAt().isBefore(startOfToday)).toList();
        List<Order> counted = todays.stream().filter(order -> !isCancelled(order)).toList();
        BigDecimal revenue = sum(counted);
        long pending = live.stream().filter(order -> OrderStatus.PENDING.name().equals(order.getStatus())).count();
        long active = live.stream().filter(order -> OrderStatus.ACTIVE_NAMES.contains(order.getStatus())
                && !OrderStatus.PENDING.name().equals(order.getStatus())).count();

        List<Double> acceptMinutes = new ArrayList<>();
        for (Order order : todays) {
            Instant placed = eventAt(order, OrderStatus.PENDING);
            Instant accepted = eventAt(order, OrderStatus.PREPARING);
            if (placed != null && accepted != null) acceptMinutes.add(Duration.between(placed, accepted).toSeconds() / 60.0);
        }
        Double averageAccept = acceptMinutes.isEmpty() ? null
                : Math.round(acceptMinutes.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10.0) / 10.0;

        return new AnalyticsDtos.Today(pending, active, counted.size(), todays.size() - counted.size(), revenue,
                counted.isEmpty() ? null : revenue.divide(BigDecimal.valueOf(counted.size()), 2, RoundingMode.HALF_UP),
                averageAccept);
    }

    private AnalyticsDtos.Problems problems(Restaurant restaurant, List<Order> live) {
        LocalDateTime now = LocalDateTime.now();
        Instant nowInstant = Instant.now();
        List<Order> active = live.stream().filter(order -> OrderStatus.ACTIVE_NAMES.contains(order.getStatus())).toList();

        List<AnalyticsDtos.ProblemOrder> waiting = active.stream()
                .filter(order -> OrderStatus.PENDING.name().equals(order.getStatus())
                        && order.getCreatedAt().isBefore(now.minusMinutes(ACCEPT_WARNING_MINUTES)))
                .map(this::problem).toList();
        List<AnalyticsDtos.ProblemOrder> overdue = active.stream()
                .filter(order -> order.getEstimatedDeliveryAt() != null && order.getEstimatedDeliveryAt().isBefore(nowInstant))
                .map(this::problem).toList();

        List<Courier> couriers = courierRepository.findAllByRestaurantIdOrderByFullNameAsc(restaurant.getId());
        boolean hasCouriers = couriers.stream().anyMatch(Courier::isActive);
        boolean noActiveCourier = hasCouriers && couriers.stream()
                .noneMatch(courier -> courier.isActive() && !CourierStatus.OFFLINE.name().equals(courier.getStatus()));
        List<AnalyticsDtos.ProblemOrder> unassigned = List.of();
        if (hasCouriers) {
            List<Order> preparingDelivery = active.stream()
                    .filter(order -> !order.isPickup() && OrderStatus.PREPARING.name().equals(order.getStatus())).toList();
            Map<UUID, DeliveryAssignment> assignments = preparingDelivery.isEmpty() ? Map.of()
                    : assignmentRepository.findAllByOrderIdIn(preparingDelivery.stream().map(Order::getId).toList()).stream()
                    .collect(Collectors.toMap(assignment -> assignment.getOrder().getId(), Function.identity()));
            unassigned = preparingDelivery.stream()
                    .filter(order -> !OrderMapper.isActiveAssignment(assignments.get(order.getId())))
                    .map(this::problem).toList();
        }

        RestaurantDto view = restaurantMapper.toPublic(restaurant, restaurantService.hoursOf(restaurant.getId()));
        return new AnalyticsDtos.Problems(waiting, overdue, unassigned,
                issueRepository.countByRestaurantIdAndStatus(restaurant.getId(), "OPEN"),
                menuItemRepository.countOutOfStock(restaurant.getId()),
                "ACTIVE".equals(restaurant.getStatus()), view.getDisplayStatus(), noActiveCourier);
    }

    private AnalyticsDtos.DirectChannel direct(Restaurant restaurant, List<Order> recent, LocalDateTime windowStart) {
        UUID restaurantId = restaurant.getId();
        long customersTotal = customerRestaurantRepository.countById_RestaurantId(restaurantId);
        long customersLast30 = customerRestaurantRepository.countById_RestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, windowStart);
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (Object[] row : customerRestaurantRepository.countBySource(restaurantId)) bySource.put((String) row[0], (Long) row[1]);

        List<Order> counted = recent.stream().filter(order -> !isCancelled(order)).toList();
        BigDecimal revenue = sum(counted);
        Map<UUID, Long> ordersPerCustomer = counted.stream()
                .collect(Collectors.groupingBy(order -> order.getCustomer().getId(), Collectors.counting()));
        long ordering = ordersPerCustomer.size();
        long repeat = ordersPerCustomer.values().stream().filter(count -> count >= 2).count();

        // Tüm zamanlar: ekleyen müşterilerden ilk siparişe ve ikinci siparişe dönüşüm.
        List<Object[]> customerStats = orderRepository.customerStats(restaurantId);
        long everOrdered = customerStats.size();
        long orderedTwice = customerStats.stream().filter(row -> (Long) row[1] >= 2).count();

        BigDecimal couponDiscount = counted.stream().map(Order::getDiscountAmount)
                .filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rate = restaurant.getMarketplaceCommissionRate();
        BigDecimal saved = rate == null ? null
                : revenue.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new AnalyticsDtos.DirectChannel(customersTotal, customersLast30, bySource, counted.size(), revenue, ordering, repeat,
                ratio(repeat, ordering), ratio(Math.min(everOrdered, customersTotal), customersTotal), ratio(orderedTwice, everOrdered),
                couponDiscount, rate, saved);
    }

    private List<AnalyticsDtos.TopItem> topItems(List<Order> recent) {
        Map<UUID, long[]> quantities = new HashMap<>();
        Map<UUID, BigDecimal> revenue = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (Order order : recent) {
            if (isCancelled(order)) continue;
            for (OrderItem item : order.getItems()) {
                UUID id = item.getMenuItem().getId();
                quantities.computeIfAbsent(id, key -> new long[1])[0] += item.getQuantity();
                revenue.merge(id, item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add);
                names.putIfAbsent(id, item.getMenuItemName());
            }
        }
        return quantities.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, long[]> entry) -> entry.getValue()[0]).reversed())
                .limit(5)
                .map(entry -> new AnalyticsDtos.TopItem(entry.getKey(), names.get(entry.getKey()), entry.getValue()[0], revenue.get(entry.getKey())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.DayPoint> timeseries(UUID restaurantId, int days) {
        int span = Math.max(7, Math.min(days, 90));
        LocalDate today = BusinessClock.today();
        LocalDate first = today.minusDays(span - 1);
        List<Order> orders = orderRepository.findAllByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, BusinessClock.startOfBusinessDay(first));
        Map<LocalDate, List<Order>> byDay = orders.stream().filter(order -> !isCancelled(order))
                .collect(Collectors.groupingBy(order -> businessDate(order.getCreatedAt())));
        Map<LocalDate, Long> newCustomers = customerRestaurantRepository.findAllById_RestaurantIdOrderByCreatedAtDesc(restaurantId).stream()
                .filter(link -> link.getCreatedAt() != null)
                .collect(Collectors.groupingBy(link -> businessDate(link.getCreatedAt()), Collectors.counting()));
        List<AnalyticsDtos.DayPoint> points = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            List<Order> dayOrders = byDay.getOrDefault(day, List.of());
            points.add(new AnalyticsDtos.DayPoint(day, dayOrders.size(), sum(dayOrders), newCustomers.getOrDefault(day, 0L)));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public PageDto<AnalyticsDtos.CustomerRow> customers(UUID restaurantId, String query, int page, int size) {
        Map<UUID, Object[]> stats = new HashMap<>();
        for (Object[] row : orderRepository.customerStats(restaurantId)) stats.put((UUID) row[0], row);
        List<CustomerRestaurant> links = customerRestaurantRepository.findAllById_RestaurantIdOrderByCreatedAtDesc(restaurantId);
        Map<UUID, Customer> customers = customerRepository.findAllById(links.stream().map(link -> link.getId().getCustomerId()).toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        String term = query == null ? null : query.trim().toLowerCase();

        List<AnalyticsDtos.CustomerRow> rows = links.stream().map(link -> {
            Customer customer = customers.get(link.getId().getCustomerId());
            Object[] row = stats.get(link.getId().getCustomerId());
            boolean deleted = customer == null || customer.isDeleted();
            return new AnalyticsDtos.CustomerRow(link.getId().getCustomerId(),
                    deleted ? null : customer.getFullName(), deleted ? null : customer.getPhoneNumber(),
                    link.getSource(), link.getCreatedAt(), link.getRemovedAt() != null,
                    row == null ? 0 : (Long) row[1], row == null ? BigDecimal.ZERO : (BigDecimal) row[2],
                    row == null ? null : (LocalDateTime) row[3]);
        }).filter(row -> term == null || term.isEmpty()
                || (row.name() != null && row.name().toLowerCase().contains(term))
                || (row.phone() != null && row.phone().contains(term)))
                .sorted(Comparator.comparing(AnalyticsDtos.CustomerRow::lastOrderAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AnalyticsDtos.CustomerRow::addedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        return new PageDto<>(rows.subList(from, to), safePage, safeSize, rows.size(), (int) Math.ceil(rows.size() / (double) safeSize));
    }

    // ---------- yardımcılar ----------

    private AnalyticsDtos.ProblemOrder problem(Order order) {
        return new AnalyticsDtos.ProblemOrder(order.getId(), order.getOrderNumber(), order.getStatus(), order.getCreatedAt());
    }

    private Instant eventAt(Order order, OrderStatus status) {
        Set<String> names = status == OrderStatus.PREPARING ? Set.of("PREPARING", "CONFIRMED") : Set.of(status.name());
        Optional<OrderStatusEvent> event = order.getStatusEvents().stream().filter(value -> names.contains(value.getStatus())).findFirst();
        return event.map(OrderStatusEvent::getCreatedAt).orElse(null);
    }

    private boolean isCancelled(Order order) {
        return OrderStatus.CANCELLED.name().equals(order.getStatus());
    }

    private BigDecimal sum(List<Order> orders) {
        return orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Double ratio(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private LocalDate businessDate(LocalDateTime timestamp) {
        return timestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(BusinessClock.ZONE).toLocalDate();
    }
}
