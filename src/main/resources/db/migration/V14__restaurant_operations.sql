-- V14: Restoran operasyon alanları (herkese açık kod, konum/teslimat bölgesi, gel-al, haftalık çalışma saatleri)
-- ve sahte puan verisinin temizlenmesi.

-- 1) Herkese açık kısa kod: QR / davet bağlantısı (siparo.app/r/{code}) için.
ALTER TABLE restaurants ADD COLUMN public_code VARCHAR(12);
UPDATE restaurants
SET public_code = upper(substr(replace(id::text, '-', ''), 1, 8))
WHERE public_code IS NULL;
ALTER TABLE restaurants ALTER COLUMN public_code SET NOT NULL;
CREATE UNIQUE INDEX uk_restaurants_public_code ON restaurants(public_code);

-- 2) Konum, teslimat bölgesi ve teslimat türleri.
ALTER TABLE restaurants ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE restaurants ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE restaurants ADD COLUMN delivery_radius_km NUMERIC(5,2);
ALTER TABLE restaurants ADD COLUMN delivery_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE restaurants ADD COLUMN pickup_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) Teslimat süresi: serbest metin ("30-40 dk") yerine dakika aralığı.
ALTER TABLE restaurants ADD COLUMN delivery_time_min INT;
ALTER TABLE restaurants ADD COLUMN delivery_time_max INT;
UPDATE restaurants
SET delivery_time_min = CAST(substring(estimated_delivery_time FROM '(\d+)') AS INT),
    delivery_time_max = COALESCE(
        CAST(substring(estimated_delivery_time FROM '\d+\s*[-–]\s*(\d+)') AS INT),
        CAST(substring(estimated_delivery_time FROM '(\d+)') AS INT))
WHERE estimated_delivery_time ~ '\d+';

-- 4) İşletmenin kendi beyan ettiği pazaryeri komisyon oranı (yalnızca "tahmini tasarruf" hesaplaması için; herkese açık değil).
ALTER TABLE restaurants ADD COLUMN marketplace_commission_rate NUMERIC(5,2);

-- 5) Puan artık yalnızca gerçek müşteri değerlendirmelerinden hesaplanır.
--    Önceki değerler işletme tarafından elle girilmişti (varsayılan 4.5); gerçek veri olmadığı için sıfırlanır.
ALTER TABLE restaurants ALTER COLUMN rating_score DROP DEFAULT;
UPDATE restaurants SET rating_score = NULL, rating_count = 0;
ALTER TABLE menu_items ALTER COLUMN like_percentage DROP DEFAULT;
UPDATE menu_items SET like_percentage = NULL, rating_count = 0;

-- 6) Haftalık çalışma saatleri (gün başına bir aralık; kapanış açılıştan küçükse gece yarısını aşar).
CREATE TABLE restaurant_opening_hours (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    CONSTRAINT uk_opening_hours_day UNIQUE (restaurant_id, day_of_week)
);
CREATE INDEX idx_opening_hours_restaurant ON restaurant_opening_hours(restaurant_id);

-- Eski tek aralık ("HH:mm-HH:mm") her güne kopyalanır.
INSERT INTO restaurant_opening_hours (id, restaurant_id, day_of_week, opens_at, closes_at)
SELECT gen_random_uuid(), r.id, d.day,
       CAST(replace(trim(split_part(r.working_hours, '-', 1)), '.', ':') AS TIME),
       CAST(replace(trim(split_part(r.working_hours, '-', 2)), '.', ':') AS TIME)
FROM restaurants r
CROSS JOIN generate_series(1, 7) AS d(day)
WHERE r.working_hours ~ '^\s*\d{1,2}[:.]\d{2}\s*-\s*\d{1,2}[:.]\d{2}\s*$';
