package com.siparo.menu;

import com.siparo.cart.CartItemRepository;
import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.order.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    /** "Popüler" etiketi için son 60 günde en az bu kadar adet satılmış olmalı. */
    private static final int POPULAR_MIN_QUANTITY = 3;
    private static final int POPULAR_LIMIT = 6;
    private static final int POPULAR_WINDOW_DAYS = 60;

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;

    public record CustomerMenuDto(List<MenuCategoryWithItemsDto> categories, List<UUID> popularItemIds) {}

    // ---------- Müşteri ----------

    /** Tek istekte tüm menü (arşivlenmemiş kategoriler/ürünler, seçenekler ve gerçek sipariş verisinden popüler ürünler). */
    @Transactional(readOnly = true)
    public CustomerMenuDto getCustomerMenu(UUID restaurantId) {
        List<MenuCategoryWithItemsDto> categories = getFullMenu(restaurantId);
        List<UUID> popular = new ArrayList<>();
        for (Object[] row : itemRepository.findTopOrdered(restaurantId, LocalDateTime.now().minusDays(POPULAR_WINDOW_DAYS))) {
            if (popular.size() >= POPULAR_LIMIT || ((Number) row[1]).longValue() < POPULAR_MIN_QUANTITY) break;
            popular.add((UUID) row[0]);
        }
        List<UUID> visibleIds = categories.stream().flatMap(category -> category.getItems().stream()).map(MenuItemDto::getId).toList();
        popular.retainAll(visibleIds);
        return new CustomerMenuDto(categories, popular);
    }

    @Transactional(readOnly = true)
    public List<MenuCategoryDto> getCategories(UUID restaurantId) {
        return categoryRepository.findAllByRestaurantIdAndArchivedFalseOrderByDisplayOrderAscNameAsc(restaurantId).stream()
                .map(this::mapCategoryToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MenuItemDto> getItemsByCategory(UUID restaurantId, UUID categoryId) {
        ownedCategory(restaurantId, categoryId);
        return itemRepository.findAllByCategoryIdAndArchivedFalseOrderByDisplayOrderAscNameAsc(categoryId).stream()
                .map(this::mapItemToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MenuCategoryWithItemsDto> getFullMenu(UUID restaurantId) {
        List<MenuCategory> categories = categoryRepository.findAllByRestaurantIdAndArchivedFalseOrderByDisplayOrderAscNameAsc(restaurantId);
        if (categories.isEmpty()) return List.of();
        Map<UUID, List<MenuItem>> itemsByCategory = itemRepository
                .findAllByCategoryIdInAndArchivedFalseOrderByDisplayOrderAscNameAsc(categories.stream().map(MenuCategory::getId).toList())
                .stream().collect(Collectors.groupingBy(item -> item.getCategory().getId()));

        return categories.stream().map(category -> {
            MenuCategoryWithItemsDto dto = new MenuCategoryWithItemsDto();
            dto.setId(category.getId());
            dto.setName(category.getName());
            dto.setDisplayOrder(category.getDisplayOrder());
            dto.setItems(itemsByCategory.getOrDefault(category.getId(), List.of()).stream().map(this::mapItemToDto).toList());
            return dto;
        }).toList();
    }

    // ---------- İşletme: kategoriler ----------

    @Transactional
    public MenuCategoryDto createCategory(UUID restaurantId, MenuCategoryDto dto) {
        MenuCategory category = new MenuCategory();
        category.setRestaurantId(restaurantId);
        category.setName(dto.getName().trim());
        category.setDisplayOrder(dto.getDisplayOrder());
        return mapCategoryToDto(categoryRepository.save(category));
    }

    @Transactional
    public MenuCategoryDto updateCategory(UUID restaurantId, UUID categoryId, MenuCategoryDto dto) {
        MenuCategory category = ownedCategory(restaurantId, categoryId);
        category.setName(dto.getName().trim());
        category.setDisplayOrder(dto.getDisplayOrder());
        return mapCategoryToDto(categoryRepository.save(category));
    }

    /** Kategori ve ürünleri kaldırılır; siparişlerde geçen ürünler geçmiş bozulmasın diye arşivlenir. */
    @Transactional
    public void deleteCategory(UUID restaurantId, UUID categoryId) {
        MenuCategory category = ownedCategory(restaurantId, categoryId);
        for (MenuItem item : itemRepository.findAllByCategoryId(categoryId)) {
            removeItem(item);
        }
        if (orderItemRepository.existsByCategoryId(categoryId)) {
            category.setArchived(true);
        } else {
            categoryRepository.delete(category);
        }
    }

    @Transactional
    public List<MenuCategoryDto> reorderCategories(UUID restaurantId, List<UUID> orderedIds) {
        Map<UUID, MenuCategory> owned = categoryRepository
                .findAllByRestaurantIdAndArchivedFalseOrderByDisplayOrderAscNameAsc(restaurantId).stream()
                .collect(Collectors.toMap(MenuCategory::getId, category -> category));
        for (int index = 0; index < orderedIds.size(); index++) {
            MenuCategory category = owned.get(orderedIds.get(index));
            if (category == null) throw new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category not found");
            category.setDisplayOrder(index);
        }
        return getCategories(restaurantId);
    }

    // ---------- İşletme: ürünler ----------

    @Transactional
    public MenuItemDto createItem(UUID restaurantId, MenuItemDto dto) {
        MenuCategory category = ownedCategory(restaurantId, dto.getCategoryId());
        MenuItem item = new MenuItem();
        item.setCategory(category);
        apply(item, dto);
        return mapItemToDto(itemRepository.save(item));
    }

    @Transactional
    public MenuItemDto updateItem(UUID restaurantId, UUID itemId, MenuItemDto dto) {
        MenuItem item = ownedItem(restaurantId, itemId);
        if (!item.getCategory().getId().equals(dto.getCategoryId())) {
            item.setCategory(ownedCategory(restaurantId, dto.getCategoryId()));
        }
        apply(item, dto);
        return mapItemToDto(itemRepository.save(item));
    }

    /** Hızlı stok anahtarı (menü tablosu ve sipariş ekranından "tükendi" işaretleme). */
    @Transactional
    public MenuItemDto updateItemStatus(UUID restaurantId, UUID itemId, String status) {
        if (!"AVAILABLE".equals(status) && !"OUT_OF_STOCK".equals(status)) {
            throw new BusinessException("INVALID_STATUS", "Status must be AVAILABLE or OUT_OF_STOCK");
        }
        MenuItem item = ownedItem(restaurantId, itemId);
        item.setStatus(status);
        return mapItemToDto(item);
    }

    @Transactional
    public void deleteItem(UUID restaurantId, UUID itemId) {
        removeItem(ownedItem(restaurantId, itemId));
    }

    private void removeItem(MenuItem item) {
        if (item.isArchived()) return;
        cartItemRepository.deleteAllByMenuItemId(item.getId());
        if (orderItemRepository.existsByMenuItemId(item.getId())) {
            item.setArchived(true);
        } else {
            itemRepository.delete(item);
        }
    }

    private void apply(MenuItem item, MenuItemDto dto) {
        if (dto.getOldPrice() != null && dto.getOldPrice().compareTo(dto.getPrice()) <= 0) {
            throw new BusinessException("INVALID_OLD_PRICE", "Previous price must be higher than the current price");
        }
        item.setName(dto.getName().trim());
        item.setDescription(dto.getDescription() == null || dto.getDescription().isBlank() ? null : dto.getDescription().trim());
        item.setPrice(dto.getPrice());
        item.setImageUrl(dto.getImageUrl() == null || dto.getImageUrl().isBlank() ? null : dto.getImageUrl().trim());
        item.setStatus(dto.getStatus());
        item.setOldPrice(dto.getOldPrice());
        item.setIsUpsell(Boolean.TRUE.equals(dto.getIsUpsell()));
        if (dto.getDisplayOrder() != null) item.setDisplayOrder(dto.getDisplayOrder());
        applyOptionGroups(item, dto.getOptionGroups() == null ? List.of() : dto.getOptionGroups());
    }

    /** Seçenek gruplarını istekteki hâline getirir; kimliği korunan grup/seçenekler güncellenir (sepetteki seçimler bozulmaz). */
    private void applyOptionGroups(MenuItem item, List<MenuItemDto.OptionGroupDto> groups) {
        Map<UUID, MenuOptionGroup> existingGroups = new HashMap<>();
        item.getOptionGroups().forEach(group -> existingGroups.put(group.getId(), group));
        List<MenuOptionGroup> result = new ArrayList<>();

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            MenuItemDto.OptionGroupDto groupDto = groups.get(groupIndex);
            int minSelect = groupDto.isRequired() ? Math.max(1, groupDto.getMinSelect()) : 0;
            int maxSelect = Math.max(groupDto.getMaxSelect(), Math.max(minSelect, 1));
            if (minSelect > groupDto.getOptions().size()) {
                throw new BusinessException("INVALID_OPTION_GROUP", "A required group needs enough options",
                        Map.of("group", groupDto.getName()));
            }
            MenuOptionGroup group = groupDto.getId() != null && existingGroups.containsKey(groupDto.getId())
                    ? existingGroups.get(groupDto.getId()) : new MenuOptionGroup();
            group.setMenuItem(item);
            group.setName(groupDto.getName().trim());
            group.setRequired(groupDto.isRequired());
            group.setMinSelect(minSelect);
            group.setMaxSelect(Math.min(maxSelect, groupDto.getOptions().size()));
            group.setDisplayOrder(groupIndex);

            Map<UUID, MenuOption> existingOptions = new HashMap<>();
            group.getOptions().forEach(option -> existingOptions.put(option.getId(), option));
            List<MenuOption> options = new ArrayList<>();
            for (int optionIndex = 0; optionIndex < groupDto.getOptions().size(); optionIndex++) {
                MenuItemDto.OptionDto optionDto = groupDto.getOptions().get(optionIndex);
                MenuOption option = optionDto.getId() != null && existingOptions.containsKey(optionDto.getId())
                        ? existingOptions.get(optionDto.getId()) : new MenuOption();
                option.setGroup(group);
                option.setName(optionDto.getName().trim());
                option.setPriceDelta(optionDto.getPriceDelta() == null ? BigDecimal.ZERO : optionDto.getPriceDelta());
                option.setAvailable(optionDto.isAvailable());
                option.setDisplayOrder(optionIndex);
                options.add(option);
            }
            group.getOptions().clear();
            group.getOptions().addAll(options);
            result.add(group);
        }
        item.getOptionGroups().clear();
        item.getOptionGroups().addAll(result);
    }

    public MenuCategory ownedCategory(UUID restaurantId, UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(category -> category.getRestaurantId().equals(restaurantId) && !category.isArchived())
                .orElseThrow(() -> new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category not found"));
    }

    private MenuItem ownedItem(UUID restaurantId, UUID itemId) {
        return itemRepository.findById(itemId)
                .filter(item -> item.getCategory().getRestaurantId().equals(restaurantId) && !item.isArchived())
                .orElseThrow(() -> new ResourceNotFoundException("MENU_ITEM_NOT_FOUND", "Item not found"));
    }

    private MenuCategoryDto mapCategoryToDto(MenuCategory category) {
        MenuCategoryDto dto = new MenuCategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDisplayOrder(category.getDisplayOrder());
        return dto;
    }

    public MenuItemDto mapItemToDto(MenuItem item) {
        MenuItemDto dto = new MenuItemDto();
        dto.setId(item.getId());
        dto.setCategoryId(item.getCategory().getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setPrice(item.getPrice());
        dto.setImageUrl(item.getImageUrl());
        dto.setStatus(item.getStatus());
        dto.setOldPrice(item.getOldPrice());
        dto.setIsUpsell(Boolean.TRUE.equals(item.getIsUpsell()));
        dto.setDisplayOrder(item.getDisplayOrder());
        dto.setOptionGroups(item.getOptionGroups().stream()
                .sorted(Comparator.comparingInt(MenuOptionGroup::getDisplayOrder))
                .map(group -> {
                    MenuItemDto.OptionGroupDto groupDto = new MenuItemDto.OptionGroupDto();
                    groupDto.setId(group.getId());
                    groupDto.setName(group.getName());
                    groupDto.setRequired(group.isRequired());
                    groupDto.setMinSelect(group.getMinSelect());
                    groupDto.setMaxSelect(group.getMaxSelect());
                    groupDto.setOptions(group.getOptions().stream()
                            .sorted(Comparator.comparingInt(MenuOption::getDisplayOrder))
                            .map(option -> {
                                MenuItemDto.OptionDto optionDto = new MenuItemDto.OptionDto();
                                optionDto.setId(option.getId());
                                optionDto.setName(option.getName());
                                optionDto.setPriceDelta(option.getPriceDelta());
                                optionDto.setAvailable(option.isAvailable());
                                return optionDto;
                            }).toList());
                    return groupDto;
                }).toList());
        return dto;
    }
}
