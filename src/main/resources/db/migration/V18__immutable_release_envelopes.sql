-- ============================================================================
-- FILE: src/main/resources/db/migration/V18__immutable_release_envelopes.sql
-- PURPOSE: Persist immutable release registrations and launch envelopes without changing legacy bindings.
-- OWNER: Platform / Orchestration
-- SINCE: week-34
-- RELATED: ADR-004, ADR-005
-- NOTES: Existing pending recommendations and approval digests remain untouched. Database rollback is separate.
-- ============================================================================
CREATE TABLE platform.ai_releases(release_id text PRIMARY KEY,checksum text NOT NULL CHECK(checksum ~ '^[0-9a-f]{64}$'),envelope jsonb NOT NULL,registered_at timestamptz NOT NULL DEFAULT now());
CREATE FUNCTION platform.reject_release_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Registered AI releases are immutable';END;
$$;
CREATE TRIGGER ai_releases_no_mutation BEFORE UPDATE OR DELETE ON platform.ai_releases FOR EACH ROW EXECUTE FUNCTION platform.reject_release_mutation();
CREATE TRIGGER ai_releases_no_truncate BEFORE TRUNCATE ON platform.ai_releases FOR EACH STATEMENT EXECUTE FUNCTION platform.reject_release_mutation();
ALTER TABLE orchestration.investigations ADD COLUMN release_envelope jsonb;
CREATE FUNCTION orchestration.preserve_release_envelope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.release_envelope IS DISTINCT FROM NEW.release_envelope THEN RAISE EXCEPTION 'Launch release is immutable'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER investigation_release_immutable BEFORE UPDATE ON orchestration.investigations FOR EACH ROW EXECUTE FUNCTION orchestration.preserve_release_envelope();
