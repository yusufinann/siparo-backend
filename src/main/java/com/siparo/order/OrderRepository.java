package com.siparo.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    Long nextOrderNumber();

    List<Order> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Optional<Order> findByIdAndCustomerId(UUID orderId, UUID customerId);

    Optional<Order> findByIdAndRestaurantId(UUID orderId, UUID restaurantId);

    long countByCustomerIdAndRestaurantIdAndStatusNot(UUID customerId, UUID restaurantId, String status);

    /** Canlı pano: aktif siparişler + bugünün tamamlanan/iptal edilenleri. */
    @Query("select o from CustomerOrder o where o.restaurant.id = :restaurantId and (o.status in :active or o.createdAt >= :since) order by o.createdAt desc")
    List<Order> findLive(@Param("restaurantId") UUID restaurantId, @Param("active") Collection<String> activeStatuses,
                         @Param("since") LocalDateTime since);

    List<Order> findAllByRestaurantIdAndCreatedAtGreaterThanEqual(UUID restaurantId, LocalDateTime from);

    /** [restaurantId, son sipariş zamanı] */
    @Query("select o.restaurant.id, max(o.createdAt) from CustomerOrder o where o.customer.id = :customerId and o.status <> 'CANCELLED' group by o.restaurant.id")
    List<Object[]> findLastOrderDatesByCustomer(@Param("customerId") UUID customerId);

    /** [customerId, sipariş sayısı, toplam tutar, son sipariş] — iptaller hariç. */
    @Query("select o.customer.id, count(o), sum(o.totalAmount), max(o.createdAt) from CustomerOrder o "
            + "where o.restaurant.id = :restaurantId and o.status <> 'CANCELLED' group by o.customer.id")
    List<Object[]> customerStats(@Param("restaurantId") UUID restaurantId);

    /** Bir müşterinin bu restorandaki (iptal hariç) ilk siparişi mi? */
    @Query("select count(o) from CustomerOrder o where o.customer.id = :customerId and o.restaurant.id = :restaurantId "
            + "and o.status <> 'CANCELLED' and o.createdAt < :before")
    long countPreviousOrders(@Param("customerId") UUID customerId, @Param("restaurantId") UUID restaurantId,
                             @Param("before") LocalDateTime before);
}
