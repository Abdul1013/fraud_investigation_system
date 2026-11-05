-- ============================================================================
-- FILE: src/main/resources/db/migration/V15__trusted_source_revisions.sql
-- PURPOSE: Require explicit approval of exact source revision hashes before model use.
-- OWNER: Knowledge & Evidence
-- SINCE: week-31
-- RELATED: ADR-005
-- NOTES: No ingestion job can implicitly trust newly imported text.
-- ============================================================================
CREATE TABLE knowledge.trusted_sources (
    source_id text NOT NULL,
    source_version int NOT NULL,
    checksum text NOT NULL CHECK(checksum ~ '^[0-9a-f]{64}$'),
    trusted_by text NOT NULL,
    trusted_at timestamptz NOT NULL DEFAULT now(),
    revoked boolean NOT NULL DEFAULT false,
    PRIMARY KEY(source_id,source_version)
);
