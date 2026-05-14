-- =====================================================================
-- V2 - KIS order audit log
-- =====================================================================
-- Orders must be auditable before real broker execution is enabled.
-- This table stores user intent, normalized order fields, broker result,
-- and trace id. Broker API credentials are never stored here.
-- =====================================================================

CREATE TABLE kis_order_audits (
    id                  UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID          NOT NULL,
    provider            VARCHAR(16)   NOT NULL,
    environment         VARCHAR(16)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    side                VARCHAR(8)    NOT NULL,
    symbol              VARCHAR(12)   NOT NULL,
    quantity            BIGINT        NOT NULL,
    price               NUMERIC(20,4) NOT NULL,
    order_division      VARCHAR(8)    NOT NULL,
    exchange_id         VARCHAR(16)   NOT NULL,
    dry_run             BOOLEAN       NOT NULL,
    trading_enabled     BOOLEAN       NOT NULL,
    broker_order_no     VARCHAR(64),
    broker_message      VARCHAR(500),
    error_code          VARCHAR(64),
    error_message       VARCHAR(1000),
    request_json        TEXT          NOT NULL,
    response_json       TEXT,
    trace_id            VARCHAR(80),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_kis_order_audits_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_kis_order_audits_user_created
    ON kis_order_audits (user_id, created_at DESC);

CREATE INDEX idx_kis_order_audits_symbol_created
    ON kis_order_audits (symbol, created_at DESC);

CREATE INDEX idx_kis_order_audits_status_created
    ON kis_order_audits (status, created_at DESC);

COMMENT ON TABLE kis_order_audits IS 'KIS order intent and broker response audit log';
