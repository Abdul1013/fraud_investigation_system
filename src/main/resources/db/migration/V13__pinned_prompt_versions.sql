-- ============================================================================
-- FILE:        src/main/resources/db/migration/V13__pinned_prompt_versions.sql
-- PURPOSE:     Freeze the chosen prompt for each newly launched durable investigation.
-- OWNER:       Fraud Investigation Engineering
-- SINCE:       week-30
-- RELATED:     context.md, curriculum week 30
-- NOTES:
--   - Keep measured results separate from fixtures and pending human review.
-- ============================================================================
ALTER TABLE orchestration.investigations ADD COLUMN prompt_reference text;
ALTER TABLE orchestration.investigations ADD COLUMN prompt_body text;
CREATE FUNCTION orchestration.protect_prompt_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.prompt_reference IS NOT NULL AND
       (NEW.prompt_reference IS DISTINCT FROM OLD.prompt_reference OR NEW.prompt_body IS DISTINCT FROM OLD.prompt_body) THEN
        RAISE EXCEPTION 'Investigation prompt binding is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER investigation_prompt_immutable BEFORE UPDATE ON orchestration.investigations
FOR EACH ROW EXECUTE FUNCTION orchestration.protect_prompt_binding();
