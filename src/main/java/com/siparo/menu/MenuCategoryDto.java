package com.siparo.menu;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MenuCategoryDto {
    private UUID id;

    @NotBlank(message = "Category name is required")
    private String name;

    private int displayOrder;
}
