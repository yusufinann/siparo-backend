package com.siparo.cart;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCustomerId(UUID customerId);

    /** Sipariş oluştururken sepet satırı kilitlenir: aynı sepetten eşzamanlı iki gönderim tek sipariş üretir. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cart from Cart cart where cart.customer.id = :customerId")
    Optional<Cart> findForUpdateByCustomerId(@Param("customerId") UUID customerId);
}