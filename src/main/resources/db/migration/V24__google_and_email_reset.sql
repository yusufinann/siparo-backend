-- Fail safely if legacy duplicate emails exist; resolve them before deployment.
UPDATE customers SET email = NULLIF(lower(trim(email)), '');
UPDATE restaurants SET email = NULLIF(lower(trim(email)), '');
CREATE UNIQUE INDEX uq_customer_email ON customers(lower(trim(email))) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_restaurant_email ON restaurants(lower(trim(email))) WHERE email IS NOT NULL;
ALTER TABLE customers ADD COLUMN google_subject VARCHAR(255) UNIQUE;
ALTER TABLE restaurants ADD COLUMN google_subject VARCHAR(255) UNIQUE;
ALTER TABLE customers ADD COLUMN sessions_invalid_before TIMESTAMP;
ALTER TABLE restaurants ADD COLUMN sessions_invalid_before TIMESTAMP;
CREATE TABLE email_password_resets (
    reset_key VARCHAR(64) PRIMARY KEY,
    owner_type VARCHAR(16) NOT NULL,
    owner_id UUID,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT false,
    grant_hash VARCHAR(64),
    grant_expires_at TIMESTAMP,
    used BOOLEAN NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX uq_reset_grant ON email_password_resets(grant_hash);
CREATE TABLE auth_rate_limits (
    bucket_key VARCHAR(64) PRIMARY KEY,
    window_start TIMESTAMP NOT NULL,
    hits INTEGER NOT NULL
);
