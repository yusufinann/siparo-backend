package com.siparo.cart;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CartItemDto {
    private UUID id;
    private UUID menuItemId;
    private String menuItemName;
    private String imageUrl;
    private BigDecimal basePrice;
    /** Ürün + seçenek farkları. */
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal lineTotal;
    private String note;
    private List<SelectedOption> options;
    /** Ürün tükendiyse/menüden kaldırıldıysa ya da seçenek artık geçerli değilse false. */
    private boolean available;

    @Getter
    @Setter
    public static class SelectedOption {
        private UUID id;
        private String groupName;
        private String name;
        private BigDecimal priceDelta;
    }
}
