package com.siparo.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCartItemRequest {
    /** 0 satırı kaldırır. */
    @NotNull
    @Min(0)
    @Max(50)
    private Integer quantity;
}
