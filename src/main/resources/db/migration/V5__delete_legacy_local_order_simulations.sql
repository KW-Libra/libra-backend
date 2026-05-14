-- =====================================================================
-- V5 - Delete legacy local order simulation records
-- =====================================================================
-- These records were never sent to KIS. From here on, only KIS paper/live
-- order attempts belong in the order audit table.
-- =====================================================================

DELETE FROM kis_order_audits
WHERE error_code = 'LOCAL_SIMULATION_REMOVED';
