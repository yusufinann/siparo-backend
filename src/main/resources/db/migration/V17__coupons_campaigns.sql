-- V17: Kupon/kampanya modeli (kod, tür, kullanım limitleri) ve kullanım kaydı.

-- V13 sabit örnek kupon ekliyordu ("Hoşgeldin İndirimi", 200 TL / min 650 TL). İşletme bunları oluşturmadı;
-- müşteriye gerçek dışı bir indirim gösterdiği için silinir. (Kupon oluşturma arayüzü V13'ten önce hiç yoktu.)
DELETE FROM coupons
WHERE title = 'Hoşgeldin İndirimi' AND discount_amount = 200 AND min_order_amount = 650;

ALTER TABLE coupons ADD COLUMN code VARCHAR(40);
UPDATE coupons SET code = upper(substr(replace(id::text, '-', ''), 1, 8)) WHERE code IS NULL;
ALTER TABLE coupons ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX uk_coupons_restaurant_code ON coupons(restaurant_id, code);

-- AMOUNT: sabit tutar, PERCENT: yüzde (isteğe bağlı üst sınır), FREE_DELIVERY: teslimat ücreti sıfırlanır.
ALTER TABLE coupons ADD COLUMN discount_type VARCHAR(20) NOT NULL DEFAULT 'AMOUNT';
ALTER TABLE coupons ALTER COLUMN discount_amount DROP NOT NULL;
ALTER TABLE coupons ADD COLUMN discount_percent NUMERIC(5,2);
ALTER TABLE coupons ADD COLUMN max_discount_amount NUMERIC(10,2);
ALTER TABLE coupons ADD COLUMN description VARCHAR(300);
ALTER TABLE coupons ADD COLUMN starts_on DATE;
ALTER TABLE coupons ADD COLUMN per_customer_limit INT NOT NULL DEFAULT 1;
ALTER TABLE coupons ADD COLUMN total_limit INT;
ALTER TABLE coupons ADD COLUMN first_order_only BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE coupon_redemptions (
    id UUID PRIMARY KEY,
    coupon_id UUID NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    discount_amount NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coupon_redemptions_order UNIQUE (order_id)
);
CREATE INDEX idx_coupon_redemptions_coupon_customer ON coupon_redemptions(coupon_id, customer_id);
