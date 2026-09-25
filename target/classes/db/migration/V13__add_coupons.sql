CREATE TABLE coupons (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    discount_amount NUMERIC(10,2) NOT NULL,
    min_order_amount NUMERIC(10,2),
    expiry_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_coupons_restaurant_id ON coupons(restaurant_id);
CREATE INDEX idx_coupons_active_expiry ON coupons(active, expiry_date);

INSERT INTO coupons (id, restaurant_id, title, discount_amount, min_order_amount, expiry_date, active)
SELECT gen_random_uuid(), id, 'Hoşgeldin İndirimi', 200, 650, CURRENT_DATE + INTERVAL '90 days', true
FROM restaurants
LIMIT 3;
