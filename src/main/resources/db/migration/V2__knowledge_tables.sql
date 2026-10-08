-- Week 26 initial knowledge schema. Flyway is the sole schema owner.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE knowledge.vector_store (
    id uuid PRIMARY KEY, content text NOT NULL, metadata jsonb NOT NULL, embedding vector(1536)
);
CREATE INDEX idx_vector_store_embedding ON knowledge.vector_store
    USING hnsw (embedding vector_cosine_ops) WITH (m=16, ef_construction=64);
CREATE INDEX idx_vector_store_metadata ON knowledge.vector_store USING gin(metadata);
CREATE TABLE knowledge.document_registry (
    id uuid PRIMARY KEY, source_id text NOT NULL, source_version int NOT NULL CHECK(source_version > 0),
    doc_type text NOT NULL, classification text NOT NULL CHECK(classification IN ('C0','C1','C2','C3')),
    effective_from timestamptz, effective_to timestamptz,
    checksum text NOT NULL, content_hash text NOT NULL, chunk_count int NOT NULL,
    ingested_at timestamptz NOT NULL DEFAULT now(), ingested_by text NOT NULL, raw_uri text NOT NULL,
    UNIQUE(source_id, source_version)
);
CREATE TABLE knowledge.chunks_lineage (
    id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES knowledge.document_registry(id),
    chunk_index int NOT NULL, chunk_hash text NOT NULL,
    vector_id uuid NOT NULL REFERENCES knowledge.vector_store(id), UNIQUE(document_id, chunk_index)
);
CREATE TABLE knowledge.ingestion_sources (
    job_instance_id bigint NOT NULL, document_id uuid NOT NULL REFERENCES knowledge.document_registry(id),
    chunks_created int NOT NULL, chunks_skipped_duplicate int NOT NULL,
    PRIMARY KEY(job_instance_id, document_id)
);
CREATE TABLE knowledge.ingestion_failures (
    job_instance_id bigint NOT NULL, source_id text NOT NULL, error text NOT NULL,
    PRIMARY KEY(job_instance_id, source_id)
);
CREATE TABLE knowledge.corpus_versions (
    version int GENERATED ALWAYS AS IDENTITY PRIMARY KEY, run_id bigint NOT NULL UNIQUE,
    promoted_at timestamptz NOT NULL DEFAULT now(), document_count int NOT NULL, chunk_count int NOT NULL
);
CREATE TABLE knowledge.corpus_members (
    corpus_version int NOT NULL REFERENCES knowledge.corpus_versions(version),
    document_id uuid NOT NULL REFERENCES knowledge.document_registry(id),
    PRIMARY KEY(corpus_version, document_id)
);
CREATE TABLE knowledge.ingestion_runs (
    run_id bigint PRIMARY KEY, job_instance_id bigint NOT NULL, corpus_version int REFERENCES knowledge.corpus_versions(version),
    started_at timestamptz NOT NULL, completed_at timestamptz NOT NULL, status text NOT NULL,
    documents_processed int NOT NULL, chunks_created int NOT NULL, chunks_skipped_duplicate int NOT NULL,
    report_json jsonb NOT NULL
);
