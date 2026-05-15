-- =====================================================================
-- V6 - KIS order idempotency key
-- =====================================================================
-- Optional client-provided key to prevent accidental duplicate broker
-- orders during retries. Credentials and account secrets are not stored.
-- =====================================================================

ALTER TABLE kis_order_audits
    ADD COLUMN idempotency_key VARCHAR(80);

CREATE UNIQUE INDEX uq_kis_order_audits_user_idempotency_key
    ON kis_order_audits (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
