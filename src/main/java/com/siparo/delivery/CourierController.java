package com.siparo.delivery;

import com.siparo.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/couriers/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COURIER')")
public class CourierController {
    private final CourierService courierService;
    private final DeliveryService deliveryService;

    @GetMapping
    public ResponseEntity<CourierDto> profile(Authentication authentication) {
        return ResponseEntity.ok(courierService.profile(CurrentUser.id(authentication)));
    }

    @GetMapping("/summary")
    public ResponseEntity<DeliveryService.CourierSummary> summary(Authentication authentication) {
        return ResponseEntity.ok(deliveryService.summary(CurrentUser.id(authentication)));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<List<DeliveryDto>> deliveries(Authentication authentication,
                                                        @RequestParam(defaultValue = "active") String scope) {
        return ResponseEntity.ok(deliveryService.listForCourier(CurrentUser.id(authentication), scope));
    }

    @GetMapping("/deliveries/{assignmentId}")
    public ResponseEntity<DeliveryDto> delivery(Authentication authentication, @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(deliveryService.getForCourier(CurrentUser.id(authentication), assignmentId));
    }

    @PostMapping("/deliveries/{assignmentId}/accept")
    public ResponseEntity<DeliveryDto> accept(Authentication authentication, @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(deliveryService.accept(CurrentUser.id(authentication), assignmentId));
    }

    @PostMapping("/deliveries/{assignmentId}/reject")
    public ResponseEntity<DeliveryDto> reject(Authentication authentication, @PathVariable UUID assignmentId,
                                              @Valid @RequestBody DeliveryRequests.Reason request) {
        return ResponseEntity.ok(deliveryService.reject(CurrentUser.id(authentication), assignmentId, request.reason()));
    }

    @PostMapping("/deliveries/{assignmentId}/start")
    public ResponseEntity<DeliveryDto> start(Authentication authentication, @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(deliveryService.start(CurrentUser.id(authentication), assignmentId));
    }

    @PostMapping("/deliveries/{assignmentId}/location")
    public ResponseEntity<DeliveryDto> location(Authentication authentication, @PathVariable UUID assignmentId,
                                                @Valid @RequestBody DeliveryRequests.Location request) {
        return ResponseEntity.ok(deliveryService.updateLocation(CurrentUser.id(authentication), assignmentId, request));
    }

    @PostMapping("/deliveries/{assignmentId}/complete")
    public ResponseEntity<DeliveryDto> complete(Authentication authentication, @PathVariable UUID assignmentId,
                                                @Valid @RequestBody DeliveryRequests.Complete request) {
        return ResponseEntity.ok(deliveryService.complete(CurrentUser.id(authentication), assignmentId, request.pin()));
    }

    @PostMapping("/deliveries/{assignmentId}/fail")
    public ResponseEntity<DeliveryDto> fail(Authentication authentication, @PathVariable UUID assignmentId,
                                            @Valid @RequestBody DeliveryRequests.Reason request) {
        return ResponseEntity.ok(deliveryService.fail(CurrentUser.id(authentication), assignmentId, request.reason()));
    }

    @PatchMapping("/availability")
    public ResponseEntity<CourierDto> availability(Authentication authentication,
                                                   @Valid @RequestBody DeliveryRequests.Availability request) {
        return ResponseEntity.ok(courierService.updateAvailability(CurrentUser.id(authentication), request.status()));
    }
}
