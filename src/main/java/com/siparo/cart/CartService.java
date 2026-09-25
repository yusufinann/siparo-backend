package com.siparo.cart;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.coupon.CouponService;
import com.siparo.customer.Customer;
import com.siparo.customer.CustomerService;
import com.siparo.menu.MenuItem;
import com.siparo.menu.MenuItemRepository;
import com.siparo.menu.MenuOption;
import com.siparo.order.PricingService;
import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantDto;
import com.siparo.restaurant.RestaurantMapper;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final int MAX_LINE_QUANTITY = 50;

    private final CartRepository cartRepository;
    private final CustomerService customerService;
    private final RestaurantService restaurantService;
    private final RestaurantMapper restaurantMapper;
    private final MenuItemRepository menuItemRepository;
    private final PricingService pricingService;
    private final CouponService couponService;

    /** Sepet satırının güncel durumu (fiyat, seçenekler, geçerlilik). */
    public record CartLine(CartItem entity, PricingService.ResolvedLine resolved, boolean available) {}

    @Transactional(readOnly = true)
    public CartDto getCart(UUID customerId, String fulfillmentType) {
        return cartRepository.findByCustomerId(customerId)
                .map(cart -> toDto(cart, customerId, fulfillmentType))
                .orElseGet(this::emptyCart);
    }

    @Transactional
    public CartDto addItem(UUID customerId, AddToCartRequest request) {
        Cart cart = getOrCreateCart(customerId);
        Restaurant restaurant = restaurantService.findOrThrow(request.getRestaurantId());

        MenuItem menuItem = menuItemRepository.findById(request.getMenuItemId())
                .filter(item -> item.getCategory().getRestaurantId().equals(restaurant.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("MENU_ITEM_NOT_FOUND", "Menu item not found"));
        if (!menuItem.isAvailable()) {
            throw new BusinessException("MENU_ITEM_UNAVAILABLE", "Menu item is unavailable", Map.of("item", menuItem.getName()));
        }
        List<MenuOption> options = pricingService.resolveOptions(menuItem, request.getOptionIds());

        if (cart.getRestaurant() != null && !cart.getRestaurant().getId().equals(restaurant.getId()) && !cart.getItems().isEmpty()) {
            if (!request.isReplaceCart()) {
                throw new BusinessException("CART_RESTAURANT_CONFLICT", "Cart contains items from another restaurant",
                        HttpStatus.CONFLICT, Map.of("restaurantName", cart.getRestaurant().getName()));
            }
            cart.reset();
        }
        cart.setRestaurant(restaurant);

        String signature = PricingService.optionSignature(options.stream().map(MenuOption::getId).toList());
        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getMenuItem().getId().equals(menuItem.getId())
                        && Objects.equals(item.getOptionIds(), signature) && Objects.equals(item.getNote(), note))
                .findFirst().orElse(null);

        if (existing != null) {
            existing.setQuantity(Math.min(MAX_LINE_QUANTITY, existing.getQuantity() + request.getQuantity()));
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setMenuItem(menuItem);
            item.setQuantity(request.getQuantity());
            item.setOptionIds(signature);
            item.setNote(note);
            cart.getItems().add(item);
        }
        return toDto(cartRepository.save(cart), customerId, null);
    }

    @Transactional
    public CartDto updateItemQuantity(UUID customerId, UUID cartItemId, int quantity) {
        Cart cart = getCartOrThrow(customerId);
        CartItem item = findItemOrThrow(cart, cartItemId);
        if (quantity <= 0) {
            removeLine(cart, item);
        } else {
            item.setQuantity(Math.min(MAX_LINE_QUANTITY, quantity));
        }
        return toDto(cartRepository.save(cart), customerId, null);
    }

    @Transactional
    public CartDto removeItem(UUID customerId, UUID cartItemId) {
        Cart cart = getCartOrThrow(customerId);
        removeLine(cart, findItemOrThrow(cart, cartItemId));
        return toDto(cartRepository.save(cart), customerId, null);
    }

    @Transactional
    public void clearCart(UUID customerId) {
        cartRepository.findByCustomerId(customerId).ifPresent(cart -> {
            cart.reset();
            cartRepository.save(cart);
        });
    }

    @Transactional
    public CartDto applyCoupon(UUID customerId, String code) {
        Cart cart = getCartOrThrow(customerId);
        if (cart.getRestaurant() == null || cart.getItems().isEmpty()) {
            throw new BusinessException("CART_EMPTY", "Cart is empty");
        }
        var coupon = couponService.findForRestaurant(cart.getRestaurant().getId(), code);
        cart.setCoupon(coupon);
        CartDto dto = toDto(cartRepository.save(cart), customerId, null);
        if (dto.getCoupon() != null && !dto.getCoupon().isValid()) {
            // Geçersiz kupon sepete bağlanmaz; müşteri nedenini hata kodundan görür.
            String errorCode = dto.getCoupon().getErrorCode();
            Map<String, Object> params = dto.getCoupon().getErrorParams();
            cart.setCoupon(null);
            throw new BusinessException(errorCode, "Coupon cannot be applied", params);
        }
        return dto;
    }

    @Transactional
    public CartDto removeCoupon(UUID customerId) {
        Cart cart = getCartOrThrow(customerId);
        cart.setCoupon(null);
        return toDto(cartRepository.save(cart), customerId, null);
    }

    /** Siparişte kullanılır: satırları güncel menüye göre çözer (geçersiz satırlar {@code available=false}). */
    public List<CartLine> resolveLines(Cart cart) {
        List<CartLine> lines = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            MenuItem menuItem = item.getMenuItem();
            List<MenuOption> options;
            boolean available = menuItem.isAvailable();
            try {
                options = pricingService.resolveOptions(menuItem, PricingService.parseSignature(item.getOptionIds()));
            } catch (BusinessException invalidOptions) {
                options = List.of();
                available = false;
            }
            lines.add(new CartLine(item, pricingService.line(menuItem, options, item.getQuantity(), item.getNote()), available));
        }
        return lines;
    }

    public Cart getCartOrThrow(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new BusinessException("CART_EMPTY", "Cart is empty"));
    }

    private void removeLine(Cart cart, CartItem item) {
        cart.getItems().remove(item);
        if (cart.getItems().isEmpty()) cart.reset();
    }

    private Cart getOrCreateCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            Customer customer = customerService.findActive(customerId);
            Cart cart = new Cart();
            cart.setCustomer(customer);
            return cart;
        });
    }

    private CartItem findItemOrThrow(Cart cart, UUID cartItemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CART_ITEM_NOT_FOUND", "Item not found in cart"));
    }

    private CartDto emptyCart() {
        CartDto dto = new CartDto();
        dto.setItems(List.of());
        dto.setSubtotal(BigDecimal.ZERO);
        dto.setDeliveryFee(BigDecimal.ZERO);
        dto.setDiscount(BigDecimal.ZERO);
        dto.setTotal(BigDecimal.ZERO);
        dto.setBlockers(List.of());
        return dto;
    }

    private CartDto toDto(Cart cart, UUID customerId, String requestedFulfillment) {
        if (cart.getRestaurant() == null || cart.getItems().isEmpty()) {
            CartDto empty = emptyCart();
            empty.setId(cart.getId());
            return empty;
        }
        Restaurant restaurant = cart.getRestaurant();
        RestaurantDto view = restaurantMapper.toPublic(restaurant, restaurantService.hoursOf(restaurant.getId()));
        String fulfillment = resolveFulfillment(restaurant, requestedFulfillment);
        boolean delivery = "DELIVERY".equals(fulfillment);

        List<CartLine> lines = resolveLines(cart);
        List<PricingService.ResolvedLine> priced = lines.stream().filter(CartLine::available).map(CartLine::resolved).toList();
        PricingService.Quote quote = pricingService.quote(restaurant, priced, cart.getCoupon(), customerId, delivery);

        CartDto dto = new CartDto();
        dto.setId(cart.getId());
        dto.setRestaurantId(restaurant.getId());
        dto.setRestaurant(summary(view));
        dto.setFulfillmentType(fulfillment);
        dto.setItems(lines.stream().map(this::toItemDto).toList());
        dto.setItemCount(lines.stream().mapToInt(line -> line.entity().getQuantity()).sum());
        dto.setSubtotal(quote.subtotal());
        dto.setDeliveryFee(quote.deliveryFee());
        dto.setDiscount(quote.discount());
        dto.setTotal(quote.total());
        dto.setFreeDeliveryRemaining(quote.freeDeliveryRemaining());
        dto.setMinOrderRemaining(quote.minOrderRemaining());

        if (quote.coupon() != null) {
            CartDto.AppliedCoupon applied = new CartDto.AppliedCoupon();
            applied.setCode(quote.coupon().coupon().getCode());
            applied.setTitle(quote.coupon().coupon().getTitle());
            applied.setDiscountType(quote.coupon().coupon().getDiscountType());
            applied.setValid(quote.coupon().valid());
            applied.setErrorCode(quote.coupon().errorCode());
            applied.setErrorParams(quote.coupon().params());
            dto.setCoupon(applied);
        }

        List<String> blockers = new ArrayList<>();
        if ("TEMPORARILY_CLOSED".equals(view.getDisplayStatus())) blockers.add("RESTAURANT_TEMPORARILY_CLOSED");
        else if (!view.isOpen()) blockers.add("RESTAURANT_CLOSED");
        if (lines.stream().anyMatch(line -> !line.available())) blockers.add("MENU_ITEM_UNAVAILABLE");
        if (quote.minOrderRemaining().signum() > 0) blockers.add("BELOW_MIN_ORDER");
        dto.setBlockers(blockers);
        return dto;
    }

    private String resolveFulfillment(Restaurant restaurant, String requested) {
        if ("PICKUP".equals(requested) && restaurant.isPickupEnabled()) return "PICKUP";
        if ("DELIVERY".equals(requested) && restaurant.isDeliveryEnabled()) return "DELIVERY";
        return restaurant.isDeliveryEnabled() ? "DELIVERY" : "PICKUP";
    }

    private CartDto.RestaurantSummary summary(RestaurantDto view) {
        CartDto.RestaurantSummary summary = new CartDto.RestaurantSummary();
        summary.setId(view.getId());
        summary.setName(view.getName());
        summary.setLogoUrl(view.getLogoUrl());
        summary.setOpen(view.isOpen());
        summary.setDisplayStatus(view.getDisplayStatus());
        summary.setMinOrderAmount(view.getMinOrderAmount());
        summary.setDeliveryFee(view.getDeliveryFee());
        summary.setFreeDeliveryThreshold(view.getFreeDeliveryThreshold());
        summary.setDeliveryTimeMin(view.getDeliveryTimeMin());
        summary.setDeliveryTimeMax(view.getDeliveryTimeMax());
        summary.setDeliveryEnabled(view.isDeliveryEnabled());
        summary.setPickupEnabled(view.isPickupEnabled());
        return summary;
    }

    private CartItemDto toItemDto(CartLine line) {
        CartItem item = line.entity();
        CartItemDto dto = new CartItemDto();
        dto.setId(item.getId());
        dto.setMenuItemId(item.getMenuItem().getId());
        dto.setMenuItemName(item.getMenuItem().getName());
        dto.setImageUrl(item.getMenuItem().getImageUrl());
        dto.setBasePrice(item.getMenuItem().getPrice());
        dto.setUnitPrice(line.resolved().unitPrice());
        dto.setQuantity(item.getQuantity());
        dto.setLineTotal(line.resolved().lineTotal());
        dto.setNote(item.getNote());
        dto.setAvailable(line.available());
        dto.setOptions(line.resolved().options().stream().map(option -> {
            CartItemDto.SelectedOption selected = new CartItemDto.SelectedOption();
            selected.setId(option.getId());
            selected.setGroupName(option.getGroup().getName());
            selected.setName(option.getName());
            selected.setPriceDelta(option.getPriceDelta());
            return selected;
        }).toList());
        return dto;
    }
}
