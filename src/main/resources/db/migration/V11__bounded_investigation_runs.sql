-- ============================================================================
-- FILE:        src/main/resources/db/migration/V11__bounded_investigation_runs.sql
-- PURPOSE:     Persist bounded worker state, launch idempotency, and replayable history.
-- OWNER:       Investigation Orchestration
-- SINCE:       week-28
-- RELATED:     ADR-004, Week 28
-- NOTES:
--   - Scheduling is in-process; crash recovery and Temporal integration remain Week 29.
--   - Human waits are persisted states, never blocked worker threads.
-- ============================================================================
CREATE TABLE orchestration.investigations (
    id uuid PRIMARY KEY,
    case_id text NOT NULL,
    subject text NOT NULL,
    request_key text NOT NULL,
    goal text NOT NULL,
    state text NOT NULL CHECK (state IN ('NEW','INVESTIGATING','PENDING_APPROVAL','INSUFFICIENT_EVIDENCE','FAILED')),
    version integer NOT NULL DEFAULT 0,
    recommendation_id uuid,
    UNIQUE(subject, request_key)
);
CREATE TABLE orchestration.investigation_events (
    run_id uuid NOT NULL REFERENCES orchestration.investigations(id),
    sequence integer NOT NULL,
    event_type text NOT NULL,
    detail text NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(run_id, sequence)
);
