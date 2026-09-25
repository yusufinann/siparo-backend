package com.siparo.delivery;

import com.siparo.common.security.CustomUserDetails;
import com.siparo.common.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/courier")
@RequiredArgsConstructor
public class CourierAuthController {
    private final CourierService courierService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody DeliveryRequests.CourierLogin request) {
        Courier courier = courierService.authenticate(request);
        CustomUserDetails user = new CustomUserDetails(
                courier.getId().toString(),
                courier.getPhoneNumber(),
                courier.getPasswordHash(),
                courier.getRestaurant().getId().toString(),
                List.of(new SimpleGrantedAuthority("ROLE_COURIER"))
        );
        return ResponseEntity.ok(Map.of(
                "token", jwtService.generateToken(user),
                "role", "ROLE_COURIER"
        ));
    }
}
