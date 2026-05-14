-- =====================================================================
-- V4 - Remove local order simulation
-- =====================================================================
-- KIS paper trading is the simulation boundary. The backend no longer has
-- a separate local-only order execution path.
-- =====================================================================

UPDATE kis_order_audits
SET status = 'REJECTED',
    broker_message = NULL,
    error_code = 'LOCAL_SIMULATION_REMOVED',
    error_message = '기존 로컬 주문 시뮬레이션 기록입니다. KIS에는 전송되지 않았습니다.',
    updated_at = NOW()
WHERE status = 'DRY_RUN';

ALTER TABLE kis_order_audits
    DROP COLUMN dry_run;
