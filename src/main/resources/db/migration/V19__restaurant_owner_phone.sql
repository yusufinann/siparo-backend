-- V19: İşletme sahibinin giriş telefonu, restoranın herkese açık iletişim telefonundan ayrılır.
-- Önceden ikisi aynı alandı; ayarlardan iletişim telefonu değişince sahip giriş yapamıyordu.
ALTER TABLE restaurants ADD COLUMN owner_phone VARCHAR(50);
UPDATE restaurants SET owner_phone = phone WHERE owner_phone IS NULL;
CREATE INDEX idx_restaurants_owner_phone ON restaurants(owner_phone);
