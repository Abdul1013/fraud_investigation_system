-- ============================================================================
-- FILE: src/main/resources/db/migration/V14__verified_identity_links.sql
-- PURPOSE: Explicitly link trusted OIDC subjects to local application permissions.
-- OWNER: Platform & Operations
-- SINCE: week-31
-- RELATED: ADR-002
-- NOTES: No username or caller-controlled role can create or change a link.
-- ============================================================================
CREATE TABLE platform.identity_links (
    issuer text NOT NULL,
    external_subject text NOT NULL,
    application_subject text NOT NULL UNIQUE,
    application_role text NOT NULL CHECK(application_role IN ('INVESTIGATOR','ANALYST','AUDITOR','KNOWLEDGE_CURATOR')),
    enabled boolean NOT NULL DEFAULT true,
    PRIMARY KEY(issuer,external_subject)
);
