package com.siparo.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    @Modifying
    @Query("delete from CartItem ci where ci.menuItem.id = :menuItemId")
    void deleteAllByMenuItemId(@Param("menuItemId") UUID menuItemId);
}
