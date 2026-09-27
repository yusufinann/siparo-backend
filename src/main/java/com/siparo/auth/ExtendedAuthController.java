package com.siparo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/auth/{role:customer|admin}") @RequiredArgsConstructor
public class ExtendedAuthController {
    private final EmailPasswordResetService resets;
    private final GoogleAuthService google;
    @PostMapping("/google")
    public AuthService.Session google(@PathVariable String role, @Valid @RequestBody ExtendedAuthRequests.Google request, HttpServletRequest http) {
        return google.login(role, request, http.getRemoteAddr());
    }
    @PostMapping("/password-reset/request")
    public Map<String, Object> request(@PathVariable String role, @Valid @RequestBody ExtendedAuthRequests.ResetRequest request, HttpServletRequest http) {
        return resets.request(role, request, http.getRemoteAddr());
    }
    @PostMapping("/password-reset/verify")
    public Map<String, String> verify(@PathVariable String role, @Valid @RequestBody ExtendedAuthRequests.Verify request, HttpServletRequest http) {
        return resets.verify(role, request, http.getRemoteAddr());
    }
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirm(@PathVariable String role, @Valid @RequestBody ExtendedAuthRequests.Confirm request, HttpServletRequest http) {
        resets.confirm(role, request, http.getRemoteAddr()); return ResponseEntity.noContent().build();
    }
}
