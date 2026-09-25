package com.siparo.notification;

import com.siparo.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/api/v1/customers/me/notifications")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<NotificationService.NotificationDto>> list(Authentication authentication) {
        return ResponseEntity.ok(notificationService.list(CurrentUser.id(authentication)));
    }

    @GetMapping("/api/v1/customers/me/notifications/unread-count")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(CurrentUser.id(authentication))));
    }

    @PostMapping("/api/v1/customers/me/notifications/{id}/read")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> markRead(Authentication authentication, @PathVariable UUID id) {
        notificationService.markRead(CurrentUser.id(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/customers/me/notifications/read-all")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/customers/me/devices")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> registerCustomerDevice(Authentication authentication, @RequestBody NotificationService.RegisterDevice request) {
        notificationService.registerDevice("CUSTOMER", CurrentUser.id(authentication), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/customers/me/devices/{token}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> unregisterCustomerDevice(Authentication authentication, @PathVariable String token) {
        notificationService.unregisterDevice("CUSTOMER", CurrentUser.id(authentication), token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/couriers/me/devices")
    @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<Void> registerCourierDevice(Authentication authentication, @RequestBody NotificationService.RegisterDevice request) {
        notificationService.registerDevice("COURIER", CurrentUser.id(authentication), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/couriers/me/devices/{token}")
    @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<Void> unregisterCourierDevice(Authentication authentication, @PathVariable String token) {
        notificationService.unregisterDevice("COURIER", CurrentUser.id(authentication), token);
        return ResponseEntity.noContent().build();
    }
}
