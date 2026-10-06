--
-- ============================================================================
-- FILE:        src/main/resources/db/migration/V1__init_schemas.sql
-- PURPOSE:     Creates the bounded-context schemas used by the modular monolith.
-- OWNER:       Platform & Operations
-- SINCE:       week-26
-- RELATED:     ADR-001
-- NOTES:
--   - TODO(week-26): add context-specific ownership and grants.
--   - Schema creation is the first Flyway migration.
-- ============================================================================
--
CREATE SCHEMA IF NOT EXISTS ingestion;
CREATE SCHEMA IF NOT EXISTS detection;
CREATE SCHEMA IF NOT EXISTS case_management;
CREATE SCHEMA IF NOT EXISTS orchestration;
CREATE SCHEMA IF NOT EXISTS knowledge;
CREATE SCHEMA IF NOT EXISTS decision;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS platform;
