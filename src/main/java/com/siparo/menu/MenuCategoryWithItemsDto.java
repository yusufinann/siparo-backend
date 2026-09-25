package com.siparo.menu;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class MenuCategoryWithItemsDto {
    private UUID id;
    private String name;
    private Integer displayOrder;
    private List<MenuItemDto> items;
}
