package com.siparo.common.security;

import com.siparo.auth.AuthRequests;
import com.siparo.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Kimlik doğrulama uçları; hata yanıtları ortak sözleşmededir (INVALID_CREDENTIALS, PHONE_ALREADY_REGISTERED…). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/customer/login")
    public ResponseEntity<AuthService.Session> customerLogin(@Valid @RequestBody AuthRequests.Login request) {
        return ResponseEntity.ok(authService.loginCustomer(request));
    }

    @PostMapping("/customer/register")
    public ResponseEntity<AuthService.Session> customerRegister(@Valid @RequestBody AuthRequests.CustomerRegister request) {
        return ResponseEntity.ok(authService.registerCustomer(request));
    }

    /** Mobil oturum yenileme (müşteri/kurye): yenileme token'ı döner, yeni erişim + yenileme token'ı verilir. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthService.Session> refresh(@Valid @RequestBody AuthRequests.Refresh request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /** Mobil çıkış: bu cihazın yenileme token'ı iptal edilir (bilinmeyen token da 204). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthRequests.Refresh request) {
        if (request != null) authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/register")
    public ResponseEntity<AuthService.Session> adminRegister(@Valid @RequestBody AuthRequests.RestaurantRegister request) {
        return ResponseEntity.ok(authService.registerRestaurant(request));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<AuthService.Session> adminLogin(@Valid @RequestBody AuthRequests.Login request) {
        return ResponseEntity.ok(authService.loginRestaurant(request));
    }
}
