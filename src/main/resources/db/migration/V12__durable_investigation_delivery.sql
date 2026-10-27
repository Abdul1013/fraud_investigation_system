-- ============================================================================
-- FILE:        src/main/resources/db/migration/V12__durable_investigation_delivery.sql
-- PURPOSE:     Persist launch intent, scoped delegation, activity receipts, and decision notifications.
-- OWNER:       Fraud Investigation Engineering
-- SINCE:       week-29
-- RELATED:     context.md, curriculum week 29
-- NOTES:
--   - Keep measured results separate from fixtures and pending human review.
-- ============================================================================
CREATE TABLE orchestration.launch_outbox (
    run_id uuid PRIMARY KEY REFERENCES orchestration.investigations(id),
    delivered_at timestamptz,
    attempts integer NOT NULL DEFAULT 0
);
CREATE TABLE orchestration.worker_delegations (
    run_id uuid PRIMARY KEY REFERENCES orchestration.investigations(id),
    subject text NOT NULL,
    case_id text NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked boolean NOT NULL DEFAULT false,
    started_at timestamptz
);
CREATE TABLE orchestration.step_receipts (
    run_id uuid NOT NULL REFERENCES orchestration.investigations(id),
    step integer NOT NULL CHECK (step BETWEEN 0 AND 5),
    model_response text,
    checkpoint text,
    outcome text,
    PRIMARY KEY(run_id,step)
);
CREATE TABLE decision.proposal_operations (
    subject text NOT NULL,
    operation_key text NOT NULL,
    binding_digest text NOT NULL,
    recommendation_id uuid NOT NULL REFERENCES decision.recommendations(id),
    PRIMARY KEY(subject,operation_key)
);
CREATE TABLE decision.outcome_outbox (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recommendation_id uuid NOT NULL REFERENCES decision.recommendations(id),
    state text NOT NULL,
    delivered_at timestamptz,
    UNIQUE(recommendation_id,state)
);
ALTER TABLE orchestration.investigations ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE orchestration.investigations DROP CONSTRAINT investigations_state_check;
ALTER TABLE orchestration.investigations ADD CONSTRAINT investigations_state_check
CHECK(state IN ('NEW','INVESTIGATING','PENDING_APPROVAL','INSUFFICIENT_EVIDENCE','FAILED','CANCELLED','RECOVERY_REQUIRED'));


CREATE TABLE orchestration.execution_reservations (
    run_id uuid PRIMARY KEY REFERENCES orchestration.investigations(id),
    active boolean NOT NULL DEFAULT true
);

-- Legacy uncertain local runs require operator reconciliation; do not automatically repeat their work.
UPDATE orchestration.investigations SET state='RECOVERY_REQUIRED',version=version+1
WHERE state IN ('NEW','INVESTIGATING');
