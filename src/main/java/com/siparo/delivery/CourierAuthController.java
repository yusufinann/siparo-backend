package com.siparo.delivery;

import com.siparo.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/courier")
@RequiredArgsConstructor
public class CourierAuthController {
    private final CourierService courierService;
    private final AuthService authService;

    /** Kurye girişi: { token, role, refreshToken } (müşteri oturumuyla aynı sözleşme). */
    @PostMapping("/login")
    public ResponseEntity<AuthService.Session> login(@Valid @RequestBody DeliveryRequests.CourierLogin request) {
        return ResponseEntity.ok(authService.courierSession(courierService.authenticate(request)));
    }
}
