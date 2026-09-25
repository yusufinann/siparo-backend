package com.siparo.order;

import com.siparo.cart.AddToCartRequest;
import com.siparo.cart.CartDto;
import com.siparo.cart.CartService;
import com.siparo.common.exception.BusinessException;
import com.siparo.menu.MenuItem;
import com.siparo.menu.MenuOption;
import com.siparo.restaurant.RestaurantDto;
import com.siparo.restaurant.RestaurantMapper;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** "Tekrar sipariş ver": önceki siparişi güncel menü ve fiyatlarla sepete aktarır. */
@Service
@RequiredArgsConstructor
public class ReorderService {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final PricingService pricingService;
    private final CartService cartService;
    private final RestaurantService restaurantService;
    private final RestaurantMapper restaurantMapper;

    public record ReorderLine(UUID menuItemId, String name, int quantity, List<OrderDto.OptionSnapshot> options, String note,
                              boolean available, String reason, BigDecimal previousUnitPrice, BigDecimal currentUnitPrice) {}

    public record ReorderPreview(UUID orderId, UUID restaurantId, String restaurantName, boolean restaurantOpen,
                                 String restaurantDisplayStatus, List<ReorderLine> lines, BigDecimal subtotal) {}

    @Transactional(readOnly = true)
    public ReorderPreview preview(UUID customerId, UUID orderId) {
        Order order = orderService.customerOrder(customerId, orderId);
        RestaurantDto restaurant = restaurantMapper.toPublic(order.getRestaurant(), restaurantService.hoursOf(order.getRestaurant().getId()));
        List<ReorderLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            List<OrderDto.OptionSnapshot> options = orderMapper.readOptions(item.getOptionsJson());
            MenuItem menuItem = item.getMenuItem();
            String reason = null;
            BigDecimal current = null;
            if (!menuItem.isAvailable()) {
                reason = menuItem.isArchived() ? "REMOVED" : "OUT_OF_STOCK";
            } else {
                try {
                    List<MenuOption> resolved = pricingService.resolveOptions(menuItem, options.stream().map(OrderDto.OptionSnapshot::getId).toList());
                    current = pricingService.line(menuItem, resolved, item.getQuantity(), item.getNote()).unitPrice();
                    subtotal = subtotal.add(current.multiply(BigDecimal.valueOf(item.getQuantity())));
                } catch (BusinessException optionsChanged) {
                    reason = "OPTIONS_CHANGED";
                }
            }
            lines.add(new ReorderLine(menuItem.getId(), item.getMenuItemName(), item.getQuantity(), options, item.getNote(),
                    reason == null, reason, item.getUnitPrice(), current));
        }
        return new ReorderPreview(order.getId(), restaurant.getId(), restaurant.getName(), restaurant.isOpen(),
                restaurant.getDisplayStatus(), lines, subtotal);
    }

    /** Mevcut ürünleri sepete ekler; başka restoranın sepeti varsa {@code replaceCart} olmadan CART_RESTAURANT_CONFLICT döner. */
    @Transactional
    public CartDto apply(UUID customerId, UUID orderId, boolean replaceCart) {
        ReorderPreview preview = preview(customerId, orderId);
        List<ReorderLine> available = preview.lines().stream().filter(ReorderLine::available).toList();
        if (available.isEmpty()) {
            throw new BusinessException("REORDER_NOTHING_AVAILABLE", "None of the items are available anymore");
        }
        CartDto cart = null;
        boolean replace = replaceCart;
        for (ReorderLine line : available) {
            AddToCartRequest request = new AddToCartRequest();
            request.setRestaurantId(preview.restaurantId());
            request.setMenuItemId(line.menuItemId());
            request.setQuantity(line.quantity());
            request.setOptionIds(line.options().stream().map(OrderDto.OptionSnapshot::getId).toList());
            request.setNote(line.note());
            request.setReplaceCart(replace);
            cart = cartService.addItem(customerId, request);
            replace = false; // yalnızca ilk eklemede eski sepet boşaltılır
        }
        return cart;
    }
}
