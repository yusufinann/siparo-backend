package com.siparo.customer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddressDto {
    private UUID id;
    private String title;
    private String address;
    private String addressDetail;
    private String icon;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private Double latitude;
    private Double longitude;
}
