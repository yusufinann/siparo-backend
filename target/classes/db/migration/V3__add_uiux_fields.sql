-- V3__add_uiux_fields.sql
-- Add fields to restaurants
ALTER TABLE restaurants ADD COLUMN rating_score DECIMAL(3,1) DEFAULT 4.5;
ALTER TABLE restaurants ADD COLUMN rating_count INT DEFAULT 0;
ALTER TABLE restaurants ADD COLUMN working_hours VARCHAR(100);
ALTER TABLE restaurants ADD COLUMN tags VARCHAR(255);
ALTER TABLE restaurants ADD COLUMN free_delivery_threshold DECIMAL(10,2) DEFAULT 0.00;

-- Add fields to menu_items
ALTER TABLE menu_items ADD COLUMN old_price DECIMAL(10,2);
ALTER TABLE menu_items ADD COLUMN like_percentage INT DEFAULT 80;
ALTER TABLE menu_items ADD COLUMN rating_count INT DEFAULT 0;
ALTER TABLE menu_items ADD COLUMN is_upsell BOOLEAN DEFAULT FALSE;
