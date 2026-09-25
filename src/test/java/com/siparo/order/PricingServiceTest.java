package com.siparo.order;

import com.siparo.common.exception.BusinessException;
import com.siparo.coupon.CouponService;
import com.siparo.menu.MenuItem;
import com.siparo.menu.MenuOption;
import com.siparo.menu.MenuOptionGroup;
import com.siparo.restaurant.Restaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PricingServiceTest {
    private final PricingService pricing = new PricingService(mock(CouponService.class));

    private MenuItem itemWithSize() {
        MenuItem item = new MenuItem();
        item.setName("Adana");
        item.setPrice(new BigDecimal("300"));
        item.setStatus("AVAILABLE");
        MenuOptionGroup size = new MenuOptionGroup();
        size.setName("Boyut");
        size.setRequired(true);
        size.setMinSelect(1);
        size.setMaxSelect(1);
        size.setMenuItem(item);
        size.getOptions().add(option(size, "Normal", "0"));
        size.getOptions().add(option(size, "Büyük", "40"));
        item.getOptionGroups().add(size);
        return item;
    }

    private MenuOption option(MenuOptionGroup group, String name, String delta) {
        MenuOption option = new MenuOption();
        option.setGroup(group);
        option.setName(name);
        option.setPriceDelta(new BigDecimal(delta));
        return option;
    }

    @Test
    void requiredGroupMustBeSelected() {
        assertThatThrownBy(() -> pricing.resolveOptions(itemWithSize(), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo("OPTION_REQUIRED"));
    }

    @Test
    void cannotSelectTwoFromSingleChoiceGroup() {
        MenuItem item = itemWithSize();
        List<java.util.UUID> both = item.getOptionGroups().get(0).getOptions().stream().map(MenuOption::getId).toList();
        assertThatThrownBy(() -> pricing.resolveOptions(item, both))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo("OPTION_TOO_MANY"));
    }

    @Test
    void foreignOptionIsRejected() {
        assertThatThrownBy(() -> pricing.resolveOptions(itemWithSize(), List.of(java.util.UUID.randomUUID())))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo("OPTION_REQUIRED"));
    }

    @Test
    void lineIncludesOptionPrices() {
        MenuItem item = itemWithSize();
        MenuOption large = item.getOptionGroups().get(0).getOptions().get(1);
        PricingService.ResolvedLine line = pricing.line(item, pricing.resolveOptions(item, List.of(large.getId())), 2, null);
        assertThat(line.unitPrice()).isEqualByComparingTo("340");
        assertThat(line.lineTotal()).isEqualByComparingTo("680");
    }

    @Test
    void freeDeliveryThresholdAndMinimumOrder() {
        Restaurant restaurant = new Restaurant();
        restaurant.setDeliveryFee(new BigDecimal("20"));
        restaurant.setFreeDeliveryThreshold(new BigDecimal("500"));
        restaurant.setMinOrderAmount(new BigDecimal("150"));
        MenuItem item = itemWithSize();
        MenuOption normal = item.getOptionGroups().get(0).getOptions().get(0);

        PricingService.Quote small = pricing.quote(restaurant, List.of(pricing.line(item, List.of(normal), 1, null)), null, null, true);
        assertThat(small.deliveryFee()).isEqualByComparingTo("20");
        assertThat(small.total()).isEqualByComparingTo("320");
        assertThat(small.freeDeliveryRemaining()).isEqualByComparingTo("200");

        PricingService.Quote large = pricing.quote(restaurant, List.of(pricing.line(item, List.of(normal), 2, null)), null, null, true);
        assertThat(large.deliveryFee()).isEqualByComparingTo("0");

        PricingService.Quote pickup = pricing.quote(restaurant, List.of(pricing.line(item, List.of(normal), 1, null)), null, null, false);
        assertThat(pickup.deliveryFee()).isEqualByComparingTo("0");
    }

    @Test
    void zeroThresholdMeansNoFreeDelivery() {
        Restaurant restaurant = new Restaurant();
        restaurant.setDeliveryFee(new BigDecimal("20"));
        restaurant.setFreeDeliveryThreshold(BigDecimal.ZERO);
        assertThat(pricing.deliveryFee(restaurant, new BigDecimal("10000"), true)).isEqualByComparingTo("20");
    }
}
