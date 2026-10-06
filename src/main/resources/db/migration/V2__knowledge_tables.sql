--
-- FILE:        src/main/resources/db/migration/V2__knowledge_tables.sql
-- PURPOSE:     Creates the versioned knowledge, chunk, and vector-store tables.
-- OWNER:       Knowledge & Evidence
-- SINCE:       week-2
-- RELATED:     ADR-005
-- NOTES:
--   - TODO(week-2): add tenant-specific retention and access policies.
--   - Vector dimensions currently match the scaffold embedding contract.
--

-- enable required extensions for vector and hstore support
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Spring AI PgVectorStore table
CREATE TABLE IF NOT EXISTS knowledge.vector_store (
  id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
  content text,
  metadata jsonb,
  embedding vector(1536)
);
---HNSW index for fast vector search (production default)
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
  ON knowledge.vector_store USING hnsw (embedding vector_cosine_ops);
  WITH (m=16, ef_construction = 64);

-- GIN index for metadata filtering 
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata
  ON knowledge.vector_store USING gin (metadata jsonb_path_ops);

-- Document registry for lineage tracking (ADR-005) 

CREATE TABLE IF NOT EXISTS knowledge.document_registry (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  source_id text NOT NULL,
  source_version int NOT NULL,
  doc_type text NOT NULL,
  classification text NOT NULL,
  effective_from timestamptz,
  effective_to timestamptz,
  checksum text NOT NULL,
  content_hash text NOT NULL,
  chunk_count int NOT NULL DEFAULT 0,
  ingested_at timestamptz NOT NULL DEFAULT now(),
  ingested_by text NOT NULL,
  raw_uri text NOT NULL,
  UNIQUE (source_id, source_version)
);

-- chunk lineage: maps chunk back to source document and chunk index for traceability
CREATE TABLE IF NOT EXISTS knowledge.chunks_lineage (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  document_id uuid NOT NULL REFERENCES knowledge.document_registry(id),
  chunk_index int NOT NULL,
  chunk_hash text NOT NULL,
  token_count int NOT NULL,
  vector_id uuid,
  UNIQUE (document_id, chunk_index)
);
CREATE INDEX IF NOT EXISTS idx_chunks_lineage_document ON knowledge.chunks_lineage(document_id);

-- Corpus versioning: tracks the version of the knowledge corpus and its ingestion run
CREATE TABLE IF NOT EXISTS knowledge.corpus_versions (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  version int NOT NULL UNIQUE,
  promoted_at timestamptz NOT NULL DEFAULT now(),
  run_id text NOT NULL,
  document_count int NOT NULL,
  chunk_count int NOT NULL,
  notes text
);

-- Ingestion run reports 
CREATE TABLE IF NOT EXISTS knowledge.ingestion_runs (
  run_id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  corpus_version int,
  started_at timestamptz NOT NULL,
  completed_at timestamptz,
  status text NOT NULL,
  documents_processed int DEFAULT 0,
  chunks_created int DEFAULT 0,
  chunks_skipped_duplicate int DEFAULT 0,
  failures jsonb DEFAULT '[]'::jsonb,
  source_traceability jsonb DEFAULT '{}'::jsonb,
);
