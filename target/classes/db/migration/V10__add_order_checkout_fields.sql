ALTER TABLE orders
    ADD COLUMN subtotal DECIMAL(10,2),
    ADD COLUMN delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN payment_method VARCHAR(30),
    ADD COLUMN note VARCHAR(500),
    ADD COLUMN delivery_latitude DOUBLE PRECISION,
    ADD COLUMN delivery_longitude DOUBLE PRECISION;

UPDATE orders SET subtotal = total_amount WHERE subtotal IS NULL;
