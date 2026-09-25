package com.siparo.order;

import com.siparo.common.exception.BusinessException;
import com.siparo.coupon.Coupon;
import com.siparo.coupon.CouponService;
import com.siparo.menu.MenuItem;
import com.siparo.menu.MenuOption;
import com.siparo.menu.MenuOptionGroup;
import com.siparo.restaurant.Restaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sepet ve siparişin tek fiyat motoru. Tüm tutarlar sunucuda, güncel menü fiyatlarıyla hesaplanır.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final CouponService couponService;

    public record ResolvedLine(MenuItem item, List<MenuOption> options, int quantity, String note,
                               BigDecimal unitPrice, BigDecimal optionsTotal, BigDecimal lineTotal) {}

    public record Quote(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal discount, BigDecimal total,
                        CouponService.Evaluation coupon, BigDecimal freeDeliveryRemaining, BigDecimal minOrderRemaining) {}

    /**
     * Seçilen seçenekleri doğrular: her seçenek bu ürüne ait ve mevcut olmalı, grup min/max kuralları sağlanmalı.
     * Hata kodları: OPTION_INVALID, OPTION_UNAVAILABLE, OPTION_REQUIRED, OPTION_TOO_MANY (params.group).
     */
    public List<MenuOption> resolveOptions(MenuItem item, Collection<UUID> optionIds) {
        Set<UUID> selected = optionIds == null ? Set.of() : Set.copyOf(optionIds);
        List<MenuOption> result = new ArrayList<>();
        int matched = 0;
        for (MenuOptionGroup group : item.getOptionGroups()) {
            List<MenuOption> chosen = group.getOptions().stream().filter(option -> selected.contains(option.getId())).toList();
            matched += chosen.size();
            for (MenuOption option : chosen) {
                if (!option.isAvailable()) {
                    throw new BusinessException("OPTION_UNAVAILABLE", "Option is unavailable", Map.of("option", option.getName()));
                }
            }
            if (chosen.size() < group.getMinSelect()) {
                throw new BusinessException("OPTION_REQUIRED", "Required option is missing", Map.of("group", group.getName()));
            }
            if (chosen.size() > group.getMaxSelect()) {
                throw new BusinessException("OPTION_TOO_MANY", "Too many options selected",
                        Map.of("group", group.getName(), "max", group.getMaxSelect()));
            }
            result.addAll(chosen);
        }
        if (matched != selected.size()) {
            throw new BusinessException("OPTION_INVALID", "Selected option does not belong to this item");
        }
        return result;
    }

    public ResolvedLine line(MenuItem item, List<MenuOption> options, int quantity, String note) {
        BigDecimal optionsTotal = options.stream().map(MenuOption::getPriceDelta).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unit = item.getPrice().add(optionsTotal);
        return new ResolvedLine(item, options, quantity, note, unit, optionsTotal, unit.multiply(BigDecimal.valueOf(quantity)));
    }

    public BigDecimal deliveryFee(Restaurant restaurant, BigDecimal subtotal, boolean delivery) {
        if (!delivery) return BigDecimal.ZERO;
        BigDecimal fee = restaurant.getDeliveryFee() == null ? BigDecimal.ZERO : restaurant.getDeliveryFee();
        BigDecimal threshold = restaurant.getFreeDeliveryThreshold();
        boolean free = threshold != null && threshold.signum() > 0 && subtotal.compareTo(threshold) >= 0;
        return free ? BigDecimal.ZERO : fee;
    }

    public Quote quote(Restaurant restaurant, List<ResolvedLine> lines, Coupon coupon, UUID customerId, boolean delivery) {
        BigDecimal subtotal = lines.stream().map(ResolvedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryFee = deliveryFee(restaurant, subtotal, delivery);

        CouponService.Evaluation evaluation = coupon == null ? null
                : couponService.evaluate(coupon, customerId, subtotal, deliveryFee, delivery);
        BigDecimal discount = evaluation != null && evaluation.valid() ? evaluation.discount() : BigDecimal.ZERO;

        BigDecimal total = subtotal.add(deliveryFee).subtract(discount).max(BigDecimal.ZERO);

        BigDecimal threshold = restaurant.getFreeDeliveryThreshold();
        BigDecimal freeDeliveryRemaining = delivery && threshold != null && threshold.signum() > 0
                && restaurant.getDeliveryFee() != null && restaurant.getDeliveryFee().signum() > 0
                ? threshold.subtract(subtotal).max(BigDecimal.ZERO) : null;
        BigDecimal min = restaurant.getMinOrderAmount() == null ? BigDecimal.ZERO : restaurant.getMinOrderAmount();
        BigDecimal minOrderRemaining = min.subtract(subtotal).max(BigDecimal.ZERO);

        return new Quote(subtotal, deliveryFee, discount, total, evaluation, freeDeliveryRemaining, minOrderRemaining);
    }

    public static String optionSignature(Collection<UUID> optionIds) {
        return optionIds == null || optionIds.isEmpty() ? null
                : optionIds.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    }

    public static List<UUID> parseSignature(String signature) {
        if (signature == null || signature.isBlank()) return List.of();
        return java.util.Arrays.stream(signature.split(",")).map(UUID::fromString).toList();
    }
}
