package com.siparo.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
    List<MenuItem> findAllByCategoryIdAndArchivedFalseOrderByDisplayOrderAscNameAsc(UUID categoryId);

    List<MenuItem> findAllByCategoryIdInAndArchivedFalseOrderByDisplayOrderAscNameAsc(Collection<UUID> categoryIds);

    List<MenuItem> findAllByCategoryId(UUID categoryId);

    @Query("select count(i) from MenuItem i where i.category.restaurantId = :restaurantId and i.archived = false and i.status = 'OUT_OF_STOCK'")
    long countOutOfStock(@Param("restaurantId") UUID restaurantId);

    /** Son dönemde en çok sipariş edilen ürünler (iptaller hariç): [menuItemId, toplam adet]. */
    @Query("select oi.menuItem.id, sum(oi.quantity) from OrderItem oi "
            + "where oi.order.restaurant.id = :restaurantId and oi.order.createdAt >= :from and oi.order.status <> 'CANCELLED' "
            + "group by oi.menuItem.id order by sum(oi.quantity) desc")
    List<Object[]> findTopOrdered(@Param("restaurantId") UUID restaurantId, @Param("from") LocalDateTime from);
}
