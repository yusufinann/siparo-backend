package com.siparo.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record Today(long newOrders, long activeOrders, long ordersToday, long cancelledToday, BigDecimal revenueToday,
                        BigDecimal averageOrderValue, Double averageAcceptMinutes) {}

    public record ProblemOrder(UUID orderId, Long orderNumber, String status, LocalDateTime createdAt) {}

    public record Problems(List<ProblemOrder> waitingAcceptance, List<ProblemOrder> overdue, List<ProblemOrder> unassigned,
                           long openIssues, long outOfStockItems, boolean acceptingOrders, String displayStatus,
                           boolean noActiveCourier) {}

    /**
     * Siparo Direct kanalı. Tüm siparişler Siparo üzerinden verildiği için "direkt" siparişlerdir.
     * {@code estimatedCommissionSaved}: işletmenin beyan ettiği pazaryeri komisyon oranı × direkt ciro (oran yoksa null).
     */
    public record DirectChannel(long customersTotal, long customersLast30, Map<String, Long> customersBySource,
                                long ordersLast30, BigDecimal revenueLast30, long orderingCustomersLast30,
                                long repeatCustomersLast30, Double repeatRateLast30, Double activationRate,
                                Double firstToSecondRate, BigDecimal couponDiscountLast30, BigDecimal commissionRate,
                                BigDecimal estimatedCommissionSaved) {}

    public record Rating(BigDecimal score, int count) {}

    public record TopItem(UUID menuItemId, String name, long quantity, BigDecimal revenue) {}

    public record Overview(Today today, Problems problems, DirectChannel direct, Rating rating, List<TopItem> topItems) {}

    public record DayPoint(LocalDate date, long orders, BigDecimal revenue, long newCustomers) {}

    public record CustomerRow(UUID customerId, String name, String phone, String source, LocalDateTime addedAt, boolean removed,
                              long orderCount, BigDecimal totalSpent, LocalDateTime lastOrderAt) {}
}
