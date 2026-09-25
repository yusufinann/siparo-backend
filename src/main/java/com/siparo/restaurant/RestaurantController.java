package com.siparo.restaurant;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    /** Müşterinin gördüğü restoran bilgisi (herkese açık alanlar). */
    @GetMapping("/{id}")
    public ResponseEntity<RestaurantDto> getRestaurant(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.getRestaurant(id));
    }

    /** İşletme ayarları ekranı: e-posta ve komisyon gibi yalnızca sahibine açık alanlarla. */
    @GetMapping("/{id}/settings")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #id)")
    public ResponseEntity<RestaurantDto> getSettings(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.getOwnerView(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #id)")
    public ResponseEntity<RestaurantDto> updateRestaurant(@PathVariable UUID id, @Valid @RequestBody RestaurantUpdateRequest request) {
        return ResponseEntity.ok(restaurantService.updateRestaurant(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #id)")
    public ResponseEntity<RestaurantDto> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(restaurantService.updateStatus(id, body.get("status")));
    }
}
