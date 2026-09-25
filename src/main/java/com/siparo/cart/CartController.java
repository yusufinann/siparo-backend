package com.siparo.cart;

import com.siparo.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/me/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartDto> getCart(Authentication authentication,
                                           @RequestParam(required = false) String fulfillmentType) {
        return ResponseEntity.ok(cartService.getCart(CurrentUser.id(authentication), fulfillmentType));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(Authentication authentication, @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addItem(CurrentUser.id(authentication), request));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartDto> updateItem(Authentication authentication, @PathVariable UUID cartItemId,
                                              @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItemQuantity(CurrentUser.id(authentication), cartItemId, request.getQuantity()));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartDto> removeItem(Authentication authentication, @PathVariable UUID cartItemId) {
        return ResponseEntity.ok(cartService.removeItem(CurrentUser.id(authentication), cartItemId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        cartService.clearCart(CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/coupon")
    public ResponseEntity<CartDto> applyCoupon(Authentication authentication, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(cartService.applyCoupon(CurrentUser.id(authentication), body.getOrDefault("code", "")));
    }

    @DeleteMapping("/coupon")
    public ResponseEntity<CartDto> removeCoupon(Authentication authentication) {
        return ResponseEntity.ok(cartService.removeCoupon(CurrentUser.id(authentication)));
    }
}
