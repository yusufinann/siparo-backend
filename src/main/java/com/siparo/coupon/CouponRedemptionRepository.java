package com.siparo.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {
    long countByCouponIdAndCustomerId(UUID couponId, UUID customerId);

    long countByCouponId(UUID couponId);

    void deleteByOrderId(UUID orderId);

    /** [couponId, kullanım sayısı, toplam indirim] */
    @Query("select r.couponId, count(r), sum(r.discountAmount) from CouponRedemption r where r.couponId in :couponIds group by r.couponId")
    List<Object[]> usageByCoupon(@Param("couponIds") Collection<UUID> couponIds);

    /** [couponId, müşterinin kullanım sayısı] */
    @Query("select r.couponId, count(r) from CouponRedemption r where r.customerId = :customerId group by r.couponId")
    List<Object[]> usageByCustomer(@Param("customerId") UUID customerId);
}
