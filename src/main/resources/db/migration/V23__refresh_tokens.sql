-- V23: Mobil oturumlar için dönen (rotating) yenileme token'ları. Ham token saklanmaz; yalnızca SHA-256 özeti tutulur.
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    owner_type VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    replaced_by UUID
);
CREATE INDEX idx_refresh_tokens_owner ON refresh_tokens(owner_type, owner_id);
