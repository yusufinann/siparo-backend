package com.siparo.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class MenuItemDto {
    private UUID id;

    @NotNull
    private UUID categoryId;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull
    @DecimalMin("0")
    private BigDecimal price;

    @Size(max = 500)
    private String imageUrl;

    @NotBlank
    @Pattern(regexp = "AVAILABLE|OUT_OF_STOCK")
    private String status;

    @DecimalMin("0")
    private BigDecimal oldPrice;

    private Boolean isUpsell;

    private Integer displayOrder;

    @Valid
    private List<OptionGroupDto> optionGroups = new ArrayList<>();

    @Getter
    @Setter
    public static class OptionGroupDto {
        private UUID id;

        @NotBlank
        @Size(max = 100)
        private String name;

        private boolean required;

        @Min(0) @Max(20)
        private int minSelect;

        @Min(1) @Max(20)
        private int maxSelect = 1;

        @Valid
        @NotEmpty
        private List<OptionDto> options = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class OptionDto {
        private UUID id;

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotNull
        @DecimalMin("0")
        private BigDecimal priceDelta = BigDecimal.ZERO;

        private boolean available = true;
    }
}
