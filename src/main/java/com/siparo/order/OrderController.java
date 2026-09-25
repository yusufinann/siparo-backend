package com.siparo.order;

import com.siparo.cart.CartDto;
import com.siparo.common.PageDto;
import com.siparo.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private static final String OWNER = "hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #restaurantId)";

    private final OrderService orderService;
    private final ReorderService reorderService;

    // ---------- Müşteri ----------

    @PostMapping("/customers/me/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderDto> createOrder(Authentication authentication, @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(CurrentUser.id(authentication), request));
    }

    @GetMapping("/customers/me/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<OrderDto>> getMyOrders(Authentication authentication) {
        return ResponseEntity.ok(orderService.getCustomerOrders(CurrentUser.id(authentication)));
    }

    @GetMapping("/customers/me/orders/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderDto> getMyOrder(Authentication authentication, @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getCustomerOrder(CurrentUser.id(authentication), orderId));
    }

    @PostMapping("/customers/me/orders/{orderId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderDto> cancelMyOrder(Authentication authentication, @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.cancelOrderAsCustomer(CurrentUser.id(authentication), orderId));
    }

    @GetMapping("/customers/me/orders/{orderId}/reorder")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReorderService.ReorderPreview> reorderPreview(Authentication authentication, @PathVariable UUID orderId) {
        return ResponseEntity.ok(reorderService.preview(CurrentUser.id(authentication), orderId));
    }

    @PostMapping("/customers/me/orders/{orderId}/reorder")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartDto> reorder(Authentication authentication, @PathVariable UUID orderId,
                                           @RequestBody(required = false) Map<String, Boolean> body) {
        boolean replaceCart = body != null && Boolean.TRUE.equals(body.get("replaceCart"));
        return ResponseEntity.ok(reorderService.apply(CurrentUser.id(authentication), orderId, replaceCart));
    }

    // ---------- İşletme ----------

    /** scope=live: aktif siparişler + bugün tamamlananlar (operasyon panosu). */
    @GetMapping("/restaurants/{restaurantId}/orders/live")
    @PreAuthorize(OWNER)
    public ResponseEntity<List<OrderDto>> liveOrders(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(orderService.getLiveOrders(restaurantId));
    }

    @GetMapping("/restaurants/{restaurantId}/orders")
    @PreAuthorize(OWNER)
    public ResponseEntity<PageDto<OrderDto>> searchOrders(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.searchOrders(restaurantId, status, from, to, q, page, size));
    }

    @GetMapping("/restaurants/{restaurantId}/orders/{orderId}")
    @PreAuthorize(OWNER)
    public ResponseEntity<OrderDto> getRestaurantOrder(@PathVariable UUID restaurantId, @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getRestaurantOrder(restaurantId, orderId));
    }

    @PatchMapping("/restaurants/{restaurantId}/orders/{orderId}/status")
    @PreAuthorize(OWNER)
    public ResponseEntity<OrderDto> updateOrderStatus(@PathVariable UUID restaurantId, @PathVariable UUID orderId,
                                                      @RequestBody Map<String, Object> body) {
        Integer prepMinutes = body.get("prepMinutes") instanceof Number value ? value.intValue() : null;
        String status = body.get("status") instanceof String value ? value : null;
        String reason = body.get("reason") instanceof String value ? value : null;
        return ResponseEntity.ok(orderService.updateOrderStatus(restaurantId, orderId, status, reason, prepMinutes));
    }
}
