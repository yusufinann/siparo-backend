-- V18: Değerlendirmeler, sipariş sorunları, bildirim merkezi, cihaz (push) kayıtları, şifre sıfırlama kodları.

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    tags VARCHAR(255),
    comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reviews_restaurant_created ON reviews(restaurant_id, created_at DESC);

CREATE TABLE order_issues (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    type VARCHAR(30) NOT NULL,
    detail VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);
CREATE INDEX idx_order_issues_restaurant_status ON order_issues(restaurant_id, status, created_at DESC);
CREATE INDEX idx_order_issues_order ON order_issues(order_id);

-- Bildirim metni istemcide (TR/EN) type + parametrelerden üretilir.
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    restaurant_id UUID REFERENCES restaurants(id) ON DELETE CASCADE,
    params TEXT,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notifications_customer_created ON notifications(customer_id, created_at DESC);

CREATE TABLE device_tokens (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    platform VARCHAR(20),
    locale VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_device_tokens_owner ON device_tokens(owner_type, owner_id);

CREATE TABLE password_reset_codes (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(50) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_password_reset_phone ON password_reset_codes(phone_number, created_at DESC);
