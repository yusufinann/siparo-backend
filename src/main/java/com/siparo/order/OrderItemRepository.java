package com.siparo.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    boolean existsByMenuItemId(UUID menuItemId);

    @Query("select count(oi) > 0 from OrderItem oi where oi.menuItem.category.id = :categoryId")
    boolean existsByCategoryId(@Param("categoryId") UUID categoryId);
}
