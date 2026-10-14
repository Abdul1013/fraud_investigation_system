-- WEEK 27: isolate model-specific retrieval embeddings from Week 26's ingestion vectors.
-- Unconstrained vector width supports local models of different dimensions; a space row pins width.
-- Exact search is deliberate for this small corpus, so no incorrect cross-space HNSW index is created.
-- The generated English tsvector/GIN index provides a second retrieval signal without a new service.
CREATE TABLE knowledge.retrieval_spaces (
    embedding_space text PRIMARY KEY,
    dimensions int NOT NULL CHECK(dimensions > 0),
    fixture boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE knowledge.retrieval_embeddings (
    chunk_id uuid NOT NULL REFERENCES knowledge.chunks_lineage(id),
    embedding_space text NOT NULL REFERENCES knowledge.retrieval_spaces(embedding_space),
    dimensions int NOT NULL CHECK(dimensions > 0),
    embedding vector NOT NULL,
    indexed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(chunk_id, embedding_space),
    CHECK(vector_dims(embedding)=dimensions)
);
ALTER TABLE knowledge.vector_store ADD COLUMN search_text tsvector
    GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;
CREATE INDEX idx_knowledge_search_text ON knowledge.vector_store USING gin(search_text);
