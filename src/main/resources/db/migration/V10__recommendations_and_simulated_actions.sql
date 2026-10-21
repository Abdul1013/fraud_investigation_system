-- ============================================================================
-- FILE:        src/main/resources/db/migration/V10__recommendations_and_simulated_actions.sql
-- PURPOSE:     Persist immutable proposals, approval consumption, and simulator idempotency.
-- OWNER:       Decision & Action / Audit
-- SINCE:       week-28
-- RELATED:     ADR-001, ADR-002
-- NOTES:
--   - Additive learning schema; simulator effects and audit append share one database transaction.
--   - No real account system is connected and no approval credentials are seeded.
-- ============================================================================
CREATE TABLE decision.recommendations (
    id uuid PRIMARY KEY,
    case_id text NOT NULL,
    proposer text NOT NULL,
    rationale text NOT NULL CHECK (length(rationale) BETWEEN 1 AND 2000),
    binding_json text NOT NULL,
    binding_digest text NOT NULL,
    state text NOT NULL CHECK (state IN ('PENDING_APPROVAL','APPROVED','REJECTED','EXECUTED')),
    version integer NOT NULL DEFAULT 1,
    approval_id uuid UNIQUE,
    approver text,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE decision.simulated_accounts (
    case_id text PRIMARY KEY,
    frozen boolean NOT NULL DEFAULT false,
    freeze_count integer NOT NULL DEFAULT 0 CHECK (freeze_count BETWEEN 0 AND 1)
);
CREATE TABLE decision.execution_results (
    subject text NOT NULL,
    idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 100),
    request_digest text NOT NULL,
    recommendation_id uuid NOT NULL REFERENCES decision.recommendations(id),
    result_json text NOT NULL,
    PRIMARY KEY (subject, idempotency_key)
);

CREATE FUNCTION decision.protect_recommendation_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.case_id IS DISTINCT FROM OLD.case_id
       OR NEW.proposer IS DISTINCT FROM OLD.proposer OR NEW.rationale IS DISTINCT FROM OLD.rationale
       OR NEW.binding_json IS DISTINCT FROM OLD.binding_json OR NEW.binding_digest IS DISTINCT FROM OLD.binding_digest THEN
        RAISE EXCEPTION 'Recommendation binding is immutable; create a new proposal';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER recommendation_binding_immutable BEFORE UPDATE ON decision.recommendations
FOR EACH ROW EXECUTE FUNCTION decision.protect_recommendation_binding();
