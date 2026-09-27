package com.siparo.platform;

import com.siparo.restaurant.RestaurantDto;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Kimlik doğrulaması gerektirmeyen uçlar: istemci yapılandırması ve QR/davet açılış sayfası. */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final PlatformFeatures features;
    private final RestaurantService restaurantService;

    /** Yalnızca canlılık (Render health check): sunucusuz veritabanını her yoklamada uyandırmamak için DB'ye gitmez. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> body = new HashMap<>();
        body.put("features", Map.of(
                "onlinePayment", features.onlinePaymentEnabled(),
                "passwordReset", features.passwordResetEnabled(),
                "push", features.pushEnabled()));
        body.put("publicWebUrl", features.publicWebUrl());
        body.put("appStoreUrl", features.appStoreUrl());
        body.put("playStoreUrl", features.playStoreUrl());
        body.put("supportPhone", features.supportPhone());
        body.put("supportEmail", features.supportEmail());
        return ResponseEntity.ok(body);
    }

    /** QR açılış sayfası için restoranın herkese açık özeti. */
    @GetMapping("/restaurants/{code}")
    public ResponseEntity<RestaurantDto> restaurantByCode(@PathVariable String code) {
        return ResponseEntity.ok(restaurantService.getByPublicCode(code));
    }
}
