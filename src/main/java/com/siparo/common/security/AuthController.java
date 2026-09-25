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

    @PostMapping("/customer/password-reset/request")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(@Valid @RequestBody AuthRequests.ResetRequest request) {
        return ResponseEntity.ok(authService.requestPasswordReset(request));
    }

    @PostMapping("/customer/password-reset/confirm")
    public ResponseEntity<AuthService.Session> confirmPasswordReset(@Valid @RequestBody AuthRequests.ResetConfirm request) {
        return ResponseEntity.ok(authService.confirmPasswordReset(request));
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
