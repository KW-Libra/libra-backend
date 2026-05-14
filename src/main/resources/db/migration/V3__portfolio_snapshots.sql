-- =====================================================================
-- V3 - Backend-owned portfolio snapshots
-- =====================================================================
-- Backend is the system of record for portfolio/account state used by the
-- frontend, broker boundary, and agent inputs. Agent checkpoints remain
-- runtime-only.
-- =====================================================================

CREATE TABLE portfolio_snapshots (
    id                       UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                  UUID          NOT NULL,
    provider                 VARCHAR(16)   NOT NULL,
    environment              VARCHAR(16)   NOT NULL,
    source                   VARCHAR(32)   NOT NULL,
    holdings_count           INTEGER       NOT NULL,
    deposit_amount           NUMERIC(20,4),
    stock_valuation_amount   NUMERIC(20,4),
    total_valuation_amount   NUMERIC(20,4),
    net_asset_amount         NUMERIC(20,4),
    purchase_amount          NUMERIC(20,4),
    valuation_amount         NUMERIC(20,4),
    profit_loss_amount       NUMERIC(20,4),
    profit_loss_rate         NUMERIC(20,4),
    snapshot_json            TEXT          NOT NULL,
    trace_id                 VARCHAR(80),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_portfolio_snapshots_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_portfolio_snapshots_user_created
    ON portfolio_snapshots (user_id, created_at DESC);

CREATE INDEX idx_portfolio_snapshots_provider_created
    ON portfolio_snapshots (provider, environment, created_at DESC);

COMMENT ON TABLE portfolio_snapshots IS 'Backend-owned portfolio/account snapshots from broker integrations';
