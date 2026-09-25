package com.siparo.menu;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/menu")
@RequiredArgsConstructor
public class MenuController {

    private static final String OWNER = "hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #restaurantId)";

    private final MenuService menuService;

    // ---------- Müşteri (herkese açık) ----------

    @GetMapping
    public ResponseEntity<MenuService.CustomerMenuDto> getMenu(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(menuService.getCustomerMenu(restaurantId));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<MenuCategoryDto>> getCategories(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(menuService.getCategories(restaurantId));
    }

    @GetMapping("/categories/{categoryId}/items")
    public ResponseEntity<List<MenuItemDto>> getItems(@PathVariable UUID restaurantId, @PathVariable UUID categoryId) {
        return ResponseEntity.ok(menuService.getItemsByCategory(restaurantId, categoryId));
    }

    // ---------- İşletme ----------

    @GetMapping("/all")
    @PreAuthorize(OWNER)
    public ResponseEntity<List<MenuCategoryWithItemsDto>> getFullMenu(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(menuService.getFullMenu(restaurantId));
    }

    @PostMapping("/categories")
    @PreAuthorize(OWNER)
    public ResponseEntity<MenuCategoryDto> createCategory(@PathVariable UUID restaurantId, @Valid @RequestBody MenuCategoryDto dto) {
        return ResponseEntity.ok(menuService.createCategory(restaurantId, dto));
    }

    @PutMapping("/categories/{categoryId}")
    @PreAuthorize(OWNER)
    public ResponseEntity<MenuCategoryDto> updateCategory(@PathVariable UUID restaurantId, @PathVariable UUID categoryId,
                                                          @Valid @RequestBody MenuCategoryDto dto) {
        return ResponseEntity.ok(menuService.updateCategory(restaurantId, categoryId, dto));
    }

    @PutMapping("/categories/order")
    @PreAuthorize(OWNER)
    public ResponseEntity<List<MenuCategoryDto>> reorderCategories(@PathVariable UUID restaurantId, @RequestBody List<UUID> orderedIds) {
        return ResponseEntity.ok(menuService.reorderCategories(restaurantId, orderedIds));
    }

    @DeleteMapping("/categories/{categoryId}")
    @PreAuthorize(OWNER)
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID restaurantId, @PathVariable UUID categoryId) {
        menuService.deleteCategory(restaurantId, categoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items")
    @PreAuthorize(OWNER)
    public ResponseEntity<MenuItemDto> createItem(@PathVariable UUID restaurantId, @Valid @RequestBody MenuItemDto dto) {
        return ResponseEntity.ok(menuService.createItem(restaurantId, dto));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize(OWNER)
    public ResponseEntity<MenuItemDto> updateItem(@PathVariable UUID restaurantId, @PathVariable UUID itemId,
                                                  @Valid @RequestBody MenuItemDto dto) {
        return ResponseEntity.ok(menuService.updateItem(restaurantId, itemId, dto));
    }

    @PatchMapping("/items/{itemId}/status")
    @PreAuthorize(OWNER)
    public ResponseEntity<MenuItemDto> updateItemStatus(@PathVariable UUID restaurantId, @PathVariable UUID itemId,
                                                        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(menuService.updateItemStatus(restaurantId, itemId, body.get("status")));
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize(OWNER)
    public ResponseEntity<Void> deleteItem(@PathVariable UUID restaurantId, @PathVariable UUID itemId) {
        menuService.deleteItem(restaurantId, itemId);
        return ResponseEntity.noContent().build();
    }
}
