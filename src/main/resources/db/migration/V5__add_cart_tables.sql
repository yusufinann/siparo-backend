-- V5__add_cart_tables.sql
-- Add cart and cart_items tables for the shopping cart feature

CREATE TABLE carts (
                       id             UUID PRIMARY KEY,
                       customer_id    UUID NOT NULL UNIQUE REFERENCES customers(id),
                       restaurant_id  UUID REFERENCES restaurants(id),
                       created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cart_items (
                            id             UUID PRIMARY KEY,
                            cart_id        UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
                            menu_item_id   UUID NOT NULL REFERENCES menu_items(id),
                            quantity       INT NOT NULL,
                            created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance and tenancy isolation
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);