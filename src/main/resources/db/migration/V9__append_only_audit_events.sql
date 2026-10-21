-- ============================================================================
-- FILE:        src/main/resources/db/migration/V9__append_only_audit_events.sql
-- PURPOSE:     Store append-only security and action events without token bodies.
-- OWNER:       Decision & Action / Audit
-- SINCE:       week-28
-- RELATED:     ADR-001, ADR-002
-- NOTES:
--   - Additive learning schema; simulator effects and audit append share one database transaction.
--   - No real account system is connected and no approval credentials are seeded.
-- ============================================================================
CREATE TABLE audit.action_events (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    subject text NOT NULL,
    event_type text NOT NULL,
    case_id text NOT NULL,
    reference_id text NOT NULL
);
CREATE FUNCTION audit.reject_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are append-only';
END;
$$;
CREATE TRIGGER action_events_no_mutation BEFORE UPDATE OR DELETE ON audit.action_events
FOR EACH ROW EXECUTE FUNCTION audit.reject_event_mutation();
CREATE TRIGGER action_events_no_truncate BEFORE TRUNCATE ON audit.action_events
FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_event_mutation();
