package com.siparo.delivery;

import com.siparo.common.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #restaurantId)")
public class CourierManagementController {
    private final CourierService courierService;
    private final DeliveryService deliveryService;

    @PostMapping("/couriers")
    public ResponseEntity<CourierDto> createCourier(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody DeliveryRequests.CreateCourier request) {
        return ResponseEntity.ok(courierService.create(restaurantId, request));
    }

    @GetMapping("/couriers")
    public ResponseEntity<List<CourierDto>> listCouriers(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(courierService.list(restaurantId));
    }

    @PatchMapping("/couriers/{courierId}")
    public ResponseEntity<CourierDto> updateCourier(
            @PathVariable UUID restaurantId,
            @PathVariable UUID courierId,
            @RequestBody DeliveryRequests.UpdateCourier request) {
        return ResponseEntity.ok(courierService.update(restaurantId, courierId, request));
    }

    @PostMapping("/couriers/{courierId}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID restaurantId,
            @PathVariable UUID courierId,
            @Valid @RequestBody DeliveryRequests.ResetPassword request) {
        courierService.resetPassword(restaurantId, courierId, request.temporaryPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/orders/{orderId}/delivery-assignment")
    public ResponseEntity<DeliveryDto> assign(
            Authentication authentication,
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody DeliveryRequests.AssignCourier request) {
        return ResponseEntity.ok(deliveryService.assign(
                restaurantId, orderId, request.courierId(), userId(authentication)));
    }

    @PatchMapping("/orders/{orderId}/delivery-assignment")
    public ResponseEntity<DeliveryDto> reassign(
            Authentication authentication,
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody DeliveryRequests.AssignCourier request) {
        return ResponseEntity.ok(deliveryService.assign(
                restaurantId, orderId, request.courierId(), userId(authentication)));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<List<DeliveryDto>> deliveries(@PathVariable UUID restaurantId,
                                                        @RequestParam(defaultValue = "false") boolean active) {
        return ResponseEntity.ok(deliveryService.listForRestaurant(restaurantId, active));
    }

    @PostMapping("/deliveries/{assignmentId}/complete-override")
    public ResponseEntity<DeliveryDto> completeOverride(
            Authentication authentication,
            @PathVariable UUID restaurantId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody DeliveryRequests.Reason request) {
        return ResponseEntity.ok(deliveryService.completeOverride(
                restaurantId, assignmentId, request.reason(), userId(authentication)));
    }

    private String userId(Authentication authentication) {
        return ((CustomUserDetails) authentication.getPrincipal()).getId();
    }
}
