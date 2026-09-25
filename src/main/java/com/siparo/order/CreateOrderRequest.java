package com.siparo.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sipariş sunucudaki sepetten oluşturulur (kalemler ve fiyatlar istemciden alınmaz).
 * {@code expectedTotal} müşterinin onayladığı tutardır; sunucu toplamı farklıysa PRICE_CHANGED döner.
 */
@Getter
@Setter
public class CreateOrderRequest {

    @Pattern(regexp = "DELIVERY|PICKUP")
    private String fulfillmentType = "DELIVERY";

    /** Adrese teslimde zorunlu. */
    private UUID addressId;

    @NotNull
    private PaymentMethod paymentMethod;

    @Size(max = 500)
    private String note;

    private BigDecimal expectedTotal;
}
