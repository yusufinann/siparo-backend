-- V16: Sipariş numarası, gel-al, indirim; ürün seçenekleri; sepette seçenek/not; menüde arşivleme ve anlık görüntüler.

-- Sipariş numarası (okunabilir, artan): #SP-1001 gibi gösterilir.
CREATE SEQUENCE order_number_seq START WITH 1001;
ALTER TABLE orders ADD COLUMN order_number BIGINT;
UPDATE orders o SET order_number = numbered.num
FROM (SELECT id, nextval('order_number_seq') AS num FROM (SELECT id FROM orders ORDER BY created_at) ordered) numbered
WHERE o.id = numbered.id;
ALTER TABLE orders ALTER COLUMN order_number SET DEFAULT nextval('order_number_seq');
ALTER TABLE orders ALTER COLUMN order_number SET NOT NULL;
CREATE UNIQUE INDEX uk_orders_order_number ON orders(order_number);

ALTER TABLE orders ADD COLUMN fulfillment_type VARCHAR(20) NOT NULL DEFAULT 'DELIVERY';
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN coupon_id UUID;
ALTER TABLE orders ADD COLUMN coupon_code VARCHAR(40);
ALTER TABLE orders ADD COLUMN delivery_address_detail VARCHAR(255);
CREATE INDEX idx_orders_restaurant_created ON orders(restaurant_id, created_at DESC);
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);

-- Sipariş kalemi anlık görüntüsü: ürün sonradan yeniden adlandırılsa/silinse de geçmiş bozulmaz.
ALTER TABLE order_items ADD COLUMN menu_item_name VARCHAR(255);
UPDATE order_items oi SET menu_item_name = mi.name FROM menu_items mi WHERE mi.id = oi.menu_item_id;
ALTER TABLE order_items ALTER COLUMN menu_item_name SET NOT NULL;
ALTER TABLE order_items ADD COLUMN options_json TEXT;
ALTER TABLE order_items ADD COLUMN options_total NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN note VARCHAR(300);

-- Menü: siparişte kullanılmış ürün/kategori silinemez (FK), bu yüzden arşivlenir.
ALTER TABLE menu_items ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE menu_categories ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE menu_items ADD COLUMN display_order INT NOT NULL DEFAULT 0;

-- Ürün seçenek grupları (Boyut: zorunlu tek seçim, Ekstralar: isteğe bağlı çoklu seçim).
CREATE TABLE menu_option_groups (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    min_select INT NOT NULL DEFAULT 0,
    max_select INT NOT NULL DEFAULT 1,
    display_order INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_option_groups_item ON menu_option_groups(menu_item_id);

CREATE TABLE menu_options (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES menu_option_groups(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price_delta NUMERIC(10,2) NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_options_group ON menu_options(group_id);

-- Sepet kalemi artık (ürün + seçenekler + not) bileşimiyle tanımlanır.
ALTER TABLE cart_items ADD COLUMN option_ids TEXT;
ALTER TABLE cart_items ADD COLUMN note VARCHAR(300);
ALTER TABLE carts ADD COLUMN coupon_id UUID;
