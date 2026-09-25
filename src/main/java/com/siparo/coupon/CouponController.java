package com.siparo.coupon;

import com.siparo.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private static final String OWNER = "hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #restaurantId)";

    private final CouponService couponService;

    @GetMapping("/api/v1/customers/me/coupons")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<CouponDto>> myCoupons(Authentication authentication) {
        return ResponseEntity.ok(couponService.listForCustomer(CurrentUser.id(authentication)));
    }

    @GetMapping("/api/v1/restaurants/{restaurantId}/coupons")
    @PreAuthorize(OWNER)
    public ResponseEntity<List<CouponDto>> list(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(couponService.listForRestaurant(restaurantId));
    }

    @PostMapping("/api/v1/restaurants/{restaurantId}/coupons")
    @PreAuthorize(OWNER)
    public ResponseEntity<CouponDto> create(@PathVariable UUID restaurantId, @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(couponService.create(restaurantId, request));
    }

    @PutMapping("/api/v1/restaurants/{restaurantId}/coupons/{couponId}")
    @PreAuthorize(OWNER)
    public ResponseEntity<CouponDto> update(@PathVariable UUID restaurantId, @PathVariable UUID couponId,
                                            @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(couponService.update(restaurantId, couponId, request));
    }

    @DeleteMapping("/api/v1/restaurants/{restaurantId}/coupons/{couponId}")
    @PreAuthorize(OWNER)
    public ResponseEntity<Void> delete(@PathVariable UUID restaurantId, @PathVariable UUID couponId) {
        couponService.delete(restaurantId, couponId);
        return ResponseEntity.noContent().build();
    }
}
