-- =====================================================================
-- V7 - Per-user KIS credentials
-- =====================================================================
-- KIS app key/secret are stored per Libra user. Secret values are encrypted
-- by the application before persistence; only masked metadata is returned
-- through APIs.
-- =====================================================================

CREATE TABLE kis_credentials (
    id                        UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                   UUID        NOT NULL,
    enabled                   BOOLEAN     NOT NULL DEFAULT TRUE,
    trading_enabled           BOOLEAN     NOT NULL DEFAULT FALSE,
    environment               VARCHAR(16) NOT NULL,
    app_key_encrypted          TEXT        NOT NULL,
    app_secret_encrypted       TEXT        NOT NULL,
    account_number             VARCHAR(32) NOT NULL,
    account_product_code       VARCHAR(8)  NOT NULL,
    hts_id_encrypted           TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_kis_credentials_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_kis_credentials_user UNIQUE (user_id)
);

CREATE INDEX idx_kis_credentials_user_enabled
    ON kis_credentials (user_id, enabled);

COMMENT ON TABLE kis_credentials IS 'Encrypted per-user KIS Open API credentials';
