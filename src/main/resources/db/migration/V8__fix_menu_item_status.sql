-- V8__fix_menu_item_status.sql
-- The mobile app used to save menu items with status 'ACTIVE', but orders only accept 'AVAILABLE'.
-- Valid values are now AVAILABLE and OUT_OF_STOCK (validated in MenuItemDto).
UPDATE menu_items SET status = 'AVAILABLE' WHERE status = 'ACTIVE';
