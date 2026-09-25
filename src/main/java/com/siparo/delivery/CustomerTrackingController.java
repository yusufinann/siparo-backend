package com.siparo.delivery;

import com.siparo.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/me/orders")
@RequiredArgsConstructor
public class CustomerTrackingController {
    private final DeliveryService deliveryService;

    @GetMapping("/{orderId}/tracking")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TrackingDto> tracking(Authentication authentication, @PathVariable UUID orderId) {
        UUID customerId = UUID.fromString(((CustomUserDetails) authentication.getPrincipal()).getId());
        return ResponseEntity.ok(deliveryService.tracking(customerId, orderId));
    }
}
