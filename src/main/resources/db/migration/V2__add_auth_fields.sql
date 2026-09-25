-- V2__add_auth_fields.sql
-- Add email to customers and email/password to restaurants for full authentication

ALTER TABLE customers ADD COLUMN email VARCHAR(255);

ALTER TABLE restaurants ADD COLUMN email VARCHAR(255);
ALTER TABLE restaurants ADD COLUMN password_hash VARCHAR(255);
