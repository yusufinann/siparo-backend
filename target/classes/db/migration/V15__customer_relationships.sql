-- V15: Müşteri-restoran ilişkisi (kaynak, favori, listeden çıkarma), hesap silme, bildirim tercihleri, adres detayı.

ALTER TABLE customer_restaurants ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE customer_restaurants ADD COLUMN favorite BOOLEAN NOT NULL DEFAULT FALSE;
-- Listeden çıkarılan restoran kaydı silinmez: edinim metrikleri (kaç müşteri kazanıldı) korunur.
ALTER TABLE customer_restaurants ADD COLUMN removed_at TIMESTAMP;
CREATE INDEX idx_customer_restaurants_restaurant ON customer_restaurants(restaurant_id, created_at);

ALTER TABLE customers ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE customers ADD COLUMN notify_order_updates BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE customers ADD COLUMN notify_campaigns BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE customers ADD COLUMN notify_restaurant_news BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE customer_addresses ADD COLUMN address_detail VARCHAR(255);
