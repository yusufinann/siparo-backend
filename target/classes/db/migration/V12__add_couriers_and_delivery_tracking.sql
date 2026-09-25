CREATE TABLE couriers (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OFFLINE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_courier_restaurant_phone UNIQUE (restaurant_id, phone_number)
);

CREATE INDEX idx_couriers_restaurant_active_status ON couriers(restaurant_id, active, status);

CREATE TABLE delivery_assignments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id),
    courier_id UUID NOT NULL REFERENCES couriers(id),
    status VARCHAR(30) NOT NULL,
    delivery_pin_hash VARCHAR(255) NOT NULL,
    delivery_pin_encrypted VARCHAR(255) NOT NULL,
    failed_pin_attempts INTEGER NOT NULL DEFAULT 0,
    pin_locked_until TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(30),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_latitude DOUBLE PRECISION,
    last_longitude DOUBLE PRECISION,
    last_accuracy DOUBLE PRECISION,
    last_heading DOUBLE PRECISION,
    last_speed DOUBLE PRECISION,
    last_location_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_delivery_assignments_courier_status ON delivery_assignments(courier_id, status);
CREATE INDEX idx_delivery_assignments_restaurant_status ON delivery_assignments(restaurant_id, status);

CREATE TABLE courier_location_points (
    id UUID PRIMARY KEY,
    delivery_assignment_id UUID NOT NULL REFERENCES delivery_assignments(id) ON DELETE CASCADE,
    courier_id UUID NOT NULL REFERENCES couriers(id),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_courier_locations_assignment_recorded ON courier_location_points(delivery_assignment_id, recorded_at DESC);

CREATE TABLE delivery_events (
    id UUID PRIMARY KEY,
    delivery_assignment_id UUID NOT NULL REFERENCES delivery_assignments(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    reason VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_events_assignment_created ON delivery_events(delivery_assignment_id, created_at);
