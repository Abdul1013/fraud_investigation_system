-- ============================================================================
-- FILE: src/main/resources/db/migration/V16__lifecycle_trace_context.sql
-- PURPOSE: Carry sanitized W3C trace context across durable boundaries and audit linkage.
-- OWNER: Platform / Orchestration / Decision / Audit
-- SINCE: week-32
-- RELATED: ADR-004
-- NOTES: Trace identifiers are correlation metadata, never authorization credentials or model text.
-- ============================================================================
ALTER TABLE orchestration.investigations ADD COLUMN trace_parent text CHECK(trace_parent IS NULL OR trace_parent ~ '^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$');
ALTER TABLE decision.recommendations ADD COLUMN trace_parent text CHECK(trace_parent IS NULL OR trace_parent ~ '^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$');
ALTER TABLE audit.action_events ADD COLUMN trace_id text CHECK(trace_id IS NULL OR trace_id ~ '^[0-9a-f]{32}$');
