package com.siparo.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class AddToCartRequest {

    @NotNull
    private UUID restaurantId;

    @NotNull
    private UUID menuItemId;

    @Min(1)
    @Max(50)
    private int quantity = 1;

    private List<UUID> optionIds = new ArrayList<>();

    @Size(max = 300)
    private String note;

    /** Sepette başka restoranın ürünleri varsa müşteri onayıyla sepeti değiştirir; aksi halde CART_RESTAURANT_CONFLICT döner. */
    private boolean replaceCart;
}
