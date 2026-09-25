CREATE TABLE order_status_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_status_events_order ON order_status_events(order_id, created_at);

ALTER TABLE orders ADD COLUMN estimated_delivery_at TIMESTAMP WITH TIME ZONE;

UPDATE orders SET status = 'PREPARING' WHERE status = 'CONFIRMED';

INSERT INTO order_status_events (id, order_id, status, created_at)
SELECT gen_random_uuid(), id, status, created_at FROM orders;
