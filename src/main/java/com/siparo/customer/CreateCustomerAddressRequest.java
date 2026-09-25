package com.siparo.customer;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCustomerAddressRequest {
    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    @Size(max = 1000)
    private String address;

    @Size(max = 255)
    private String addressDetail;

    @Pattern(regexp = "home|work|other|location")
    private String icon;

    /** Teslimat bölgesi ve kurye navigasyonu için konum zorunludur. */
    @NotNull
    @DecimalMin("-90") @DecimalMax("90")
    private Double latitude;

    @NotNull
    @DecimalMin("-180") @DecimalMax("180")
    private Double longitude;
}
