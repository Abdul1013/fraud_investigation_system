-- ============================================================================
-- FILE:        src/main/resources/db/migration/V8__case_access_and_source_scope.sql
-- PURPOSE:     Persist case details, per-subject clearance, and server-owned evidence scope.
-- OWNER:       Case Management
-- SINCE:       week-28
-- RELATED:     ADR-001, ADR-005
-- NOTES:
--   - Additive migration; applied placeholder migrations remain unchanged.
--   - No demo grants or real accounts are seeded; provisioning is a trusted operator responsibility.
--   - Source identifiers are references, not cross-context foreign keys or case-owned knowledge rows.
-- ============================================================================
CREATE TABLE case_management.case_records (
    case_id text PRIMARY KEY CHECK (length(case_id) BETWEEN 1 AND 100 AND case_id = btrim(case_id)),
    status text NOT NULL CHECK (status IN ('NEW','TRIAGED','INVESTIGATING','PENDING_APPROVAL','RESOLVED','CLOSED','DEGRADED')),
    summary text NOT NULL CHECK (length(summary) <= 4000),
    classification text NOT NULL CHECK (classification IN ('C0','C1','C2')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE case_management.case_grants (
    case_id text NOT NULL REFERENCES case_management.case_records(case_id) ON DELETE CASCADE,
    subject text NOT NULL CHECK (length(subject) > 0 AND subject = btrim(subject)),
    classification_ceiling text NOT NULL CHECK (classification_ceiling IN ('C0','C1','C2')),
    PRIMARY KEY (case_id, subject)
);
CREATE INDEX case_grants_subject ON case_management.case_grants(subject, case_id);
CREATE TABLE case_management.case_sources (
    case_id text NOT NULL REFERENCES case_management.case_records(case_id) ON DELETE CASCADE,
    source_id text NOT NULL CHECK (length(source_id) > 0),
    PRIMARY KEY (case_id, source_id)
);
