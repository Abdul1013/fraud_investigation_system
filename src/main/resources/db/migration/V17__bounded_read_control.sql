-- ============================================================================
-- FILE: src/main/resources/db/migration/V17__bounded_read_control.sql
-- PURPOSE: Version authorization/source scope and persist cross-node admission-rate counters.
-- OWNER: Case / Knowledge / Platform / Orchestration
-- SINCE: week-33
-- RELATED: ADR-001, ADR-005
-- NOTES: Each context owns its tables. Revision tombstones survive case-ID reuse; no credential is cached.
-- ============================================================================
CREATE TABLE case_management.scope_revisions(case_id text PRIMARY KEY,revision bigint NOT NULL DEFAULT 1);
INSERT INTO case_management.scope_revisions(case_id) SELECT case_id FROM case_management.case_records;
CREATE FUNCTION case_management.bump_scope_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE affected text;
BEGIN
    affected=CASE WHEN TG_OP='DELETE' THEN OLD.case_id ELSE NEW.case_id END;
    INSERT INTO case_management.scope_revisions VALUES(affected,1) ON CONFLICT(case_id) DO UPDATE SET revision=case_management.scope_revisions.revision+1;
    RETURN NULL;
END;
$$;
CREATE TRIGGER grants_scope_revision AFTER INSERT OR UPDATE OR DELETE ON case_management.case_grants FOR EACH ROW EXECUTE FUNCTION case_management.bump_scope_revision();
CREATE TRIGGER sources_scope_revision AFTER INSERT OR UPDATE OR DELETE ON case_management.case_sources FOR EACH ROW EXECUTE FUNCTION case_management.bump_scope_revision();
CREATE TRIGGER case_scope_revision AFTER INSERT OR UPDATE OR DELETE ON case_management.case_records FOR EACH ROW EXECUTE FUNCTION case_management.bump_scope_revision();
CREATE TABLE knowledge.scope_revision(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),revision bigint NOT NULL DEFAULT 1);
INSERT INTO knowledge.scope_revision VALUES(true,1);
CREATE FUNCTION knowledge.bump_scope_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN UPDATE knowledge.scope_revision SET revision=revision+1 WHERE singleton=true;RETURN NULL;END;
$$;
CREATE TRIGGER trust_scope_revision AFTER INSERT OR UPDATE OR DELETE ON knowledge.trusted_sources FOR EACH STATEMENT EXECUTE FUNCTION knowledge.bump_scope_revision();
CREATE TRIGGER document_scope_revision AFTER INSERT OR UPDATE OR DELETE ON knowledge.document_registry FOR EACH STATEMENT EXECUTE FUNCTION knowledge.bump_scope_revision();
CREATE TRIGGER corpus_scope_revision AFTER INSERT OR UPDATE OR DELETE ON knowledge.corpus_members FOR EACH STATEMENT EXECUTE FUNCTION knowledge.bump_scope_revision();
CREATE TABLE platform.rate_buckets(subject text NOT NULL,budget text NOT NULL,window_start timestamptz NOT NULL,used integer NOT NULL,PRIMARY KEY(subject,budget,window_start));
CREATE TABLE orchestration.admission_lock(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton));
INSERT INTO orchestration.admission_lock VALUES(true);
