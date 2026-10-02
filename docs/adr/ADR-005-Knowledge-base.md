
# ADR-005: Knowledge Base as a Versioned Data Product with Source Lineage

**Status:** Accepted
**Date:** 2026-09-25
**Deciders:** Architecture Lead, Data Lead, Compliance Lead, Retrieval Lead
**Technical Area:** Data / AI / Compliance

## 1. Context

The engine's recommendations are only defensible if the evidence behind them is traceable to a specific source version. The knowledge base contains fraud policies, chargeback rules, KYC documents, prior cases, analyst notes, and regulatory guidance. These change over time: policies are updated, prior cases accumulate, and stale retrieval becomes a correctness risk, not just a quality risk.

Key constraints:

* Citations must resolve to a specific source version, not just a document title.
* Retrieval must respect data classification (C3 never embedded or retrieved by a standard service identity).
* Re-ingestion must be repeatable and retryable; a partial ingest must not corrupt the corpus.
* Duplicate and near-duplicate documents must be detected to avoid polluting retrieval.
* Document versioning must support "what did the system know at time T?" for audit.
* The knowledge base should be a managed data product with metadata, lineage, versioning, background jobs, and traceability , and citation quality and grounding checks .
* Storage and retention must align with `docs/data-classification.md` and regulatory retention.

Affected architecture: `knowledge`, `retrieval-svc`, `ingestion`, `audit`, `eval`, and the pgvector/object-store storage layer.

## 2. Decision

> We will treat the knowledge base as a **versioned data product**: every document has `source_id`, `source_version`, `effective_from`, `classification`, `checksum`, and `ingested_at`; chunks inherit these attributes; raw documents live in object storage; embeddings live in pgvector alongside metadata; retrieval always returns citations with source version; and answers without a valid citation are marked `ungrounded` and cannot support a write recommendation.

Ingestion is a retryable background job that produces a run report. Re-ingestion is versioned and idempotent. Classification ceilings are enforced at retrieval time.

## 3. Options Considered

### Option 1 — Versioned knowledge data product with lineage (chosen)

**Pros**

* Citations resolve to a specific source version; audit can reconstruct "what the system knew at time T."
* Enables groundedness evals and citation-coverage metrics.
* Supports policy-change traceability: a decision made under policy v3 is explainable after v4 ships.
* Idempotent, retryable ingestion; partial failures do not corrupt the corpus.
* Classification ceilings enforced at retrieval, keeping C3 out of embeddings.
* Deduplication and metadata enable precise retrieval filters.

**Cons**

* More pipeline complexity: version reconciliation, re-ingestion, compaction.
* Storage grows with versions; requires retention and compaction policy.
* Requires discipline: every document must carry metadata, or retrieval quality degrades.

### Option 2 — Flat document store with embeddings, no versioning

**Pros**

* Simplest to implement.
* Fast initial ingestion.

**Cons**

* Citations cannot resolve to a version; audit and explainability break.
* Re-ingestion overwrites; no way to know what the system knew at decision time.
* Deduplication is manual or absent; retrieval pollution is likely.
* Groundedness evals are unreliable.
* Fails the Week 26 deliverable and the compliance requirement.

### Option 3 — External managed knowledge base / vector store with built-in versioning

**Pros**

* Less pipeline code; managed scaling.
* Some providers offer versioning and metadata.

**Cons**

* Data residency and classification constraints may forbid external storage of C2/C3.
* Less control over chunking, metadata schema, and citation format.
* Vendor lock-in for a core data product.
* Cost grows with corpus size; harder to reason about retention.
* Rejected as the primary store, but compatible as a future backend behind `retrieval-svc`.

### Option 4 — Hybrid: object store for raw, pgvector for embeddings, Git for policies

**Pros**

* Policies as versioned text in Git align naturally with review workflows.
* Raw documents in object storage are durable and cheap.
* Embeddings in pgvector are queryable and co-located with metadata.

**Cons**

* Three storage systems to operate and keep consistent.
* Requires a reconciliation process to keep Git, object store, and pgvector aligned.

**Note:** This is effectively the implementation shape of Option 1 and is accepted as the concrete storage layout.

## 4. Rationale

* **Compliance:** Regulatory defensibility requires knowing which policy version supported a decision. Versioned lineage is the only way to answer that after the fact.
* **Maintainability:** A well-defined metadata schema and idempotent ingestion make the corpus predictable to operate and evolve.
* **Security:** Classification is a first-class attribute; retrieval enforces ceilings; C3 is never embedded. This is the data-layer counterpart to ADR-002 and ADR-003.
* **Cost:** Object storage for raw documents is cheap; pgvector co-locates embeddings and metadata to avoid a separate vector store bill. Retention and compaction keep growth bounded.
* **Scalability:** Background ingestion jobs scale independently; retrieval scales with pgvector read replicas if needed.
* **Developer experience:** A clear schema (`source_id`, `source_version`, `effective_from`, `classification`, `checksum`, `ingested_at`) makes retrieval filters obvious and testable.
* **Integration:** Citations flow into audit, eval, and the analyst approval UI; `retrieval_config_version` and `corpus_version` join the version envelope from ADR-003.
* **Team expertise:** Postgres + pgvector is already in the stack; object storage is standard; Git-backed policy review matches existing workflows.

## 5. Consequences

### Positive

* Every answer can be traced to a source version; audit and explainability are strong.
* Groundedness and citation-coverage evals are possible and meaningful.
* Policy changes are traceable; decisions remain explainable after the policy moves on.
* Ingestion is retryable and idempotent; partial failures do not corrupt the corpus.
* Deduplication and metadata filters improve retrieval precision.
* Classification ceilings keep C3 out of embeddings and external calls.

### Negative / Trade-offs

* More moving parts: object store, pgvector, Git-backed policy store, ingestion jobs.
* Storage grows with versions; retention and compaction policy required.
* Re-ingestion and version reconciliation add operational work.
* Metadata discipline is mandatory; missing metadata degrades retrieval.

### Risks

* **Stale corpus.** Mitigation: `effective_from` and `effective_to` metadata; retrieval filters by effective date; a freshness metric and alert on stale sources.
* **Duplicate / near-duplicate documents.** Mitigation: checksum-based exact dedup plus embedding-similarity near-dedup at ingest; run report flags duplicates.
* **Partial ingest corruption.** Mitigation: ingestion is transactional per document; corpus version is only promoted after a successful run; rollback to previous corpus version on failure.
* **Classification leakage.** Mitigation: classification is set at ingest and enforced at retrieval; C3 documents are stored but never embedded; retrieval service rejects queries that would cross the caller's ceiling.
* **Citation drift.** Mitigation: citations store `source_id` + `source_version` + `chunk_id` + `checksum`; re-ingestion creates a new version, never mutates an old one.
* **Unbounded storage growth.** Mitigation: retention policy per classification; compaction of superseded versions beyond the audit window; cold storage for older raw documents.

## 6. Implementation

* Metadata schema (per document):
  * `source_id` (stable identifier)
  * `source_version` (monotonic)
  * `effective_from`, `effective_to` (nullable)
  * `classification` (C0–C3)
  * `checksum` (content hash)
  * `ingested_at`, `ingested_by`
  * `doc_type` (policy, prior_case, KYC, guidance, analyst_note)
  * `jurisdiction`, `product_line` (retrieval filters)
* Chunk schema (inherits document metadata plus):
  * `chunk_id`, `chunk_index`, `chunk_hash`
  * `embedding` (pgvector)
  * `token_count`
* Storage layout:
  * Raw documents: object store (`s3://fraud-kb/{classification}/{source_id}/{source_version}.{ext}`).
  * Policies and prompts: Git-backed, reviewed via PR, loaded by `promptId`/`policyId` + version.
  * Embeddings + chunk metadata: Postgres `knowledge` schema with pgvector.
* Ingestion pipeline (background job, retryable):
  1. Load document from source.
  2. Classify; reject C3 from embedding path.
  3. Parse; extract text and structure.
  4. Chunk per `doc_type` strategy (policy: semantic sections; prior cases: structured fields; KYC: field-level, never embedded).
  5. Deduplicate (checksum exact; embedding similarity near-dup).
  6. Embed chunks (via `EmbedPort`, ADR-003).
  7. Upsert with new `source_version`; never mutate prior versions.
  8. Emit run report: counts, durations, failures, duplicates, new corpus version.
* Retrieval contract:
  * `POST /v1/retrieve` returns passages with `source_id`, `source_version`, `chunk_id`, `checksum`, `classification`, and a score.
  * Answers without ≥1 valid citation are marked `ungrounded` and cannot support a write recommendation (ADR-002).
* Classification enforcement:
  * Retrieval service reads caller identity → ceiling; rejects or redacts above ceiling.
  * C3 documents are stored (for audit) but never embedded and never returned to model-facing callers.
* Version envelope: every retrieval response includes `corpus_version` and `retrieval_config_version`, which flow into audit and eval records (ADR-003).
* Retention and compaction:
  * Per-classification retention (C2: 7y, C3: per regulation, C1: 3y, C0: 5y).
  * Superseded versions retained for the audit window, then compacted to cold storage.
* Tests:
  * Idempotent re-ingest: same document ingested twice produces one new version, no duplicates.
  * Retry test: kill ingestion mid-run; retry completes and produces a consistent corpus version.
  * Classification test: C3 never embedded; retrieval rejects ceiling violations.
  * Citation test: every answer with a citation resolves to a valid `source_id` + `source_version` + `checksum`.
  * Staleness test: retrieval filters by `effective_from`/`effective_to` correctly.
* Run report artifact: stored per ingestion run; includes counts, failures, duplicates, and the promoted `corpus_version`.

## 7. Related Decisions

* ADR-001: Modular monolith — `knowledge` is a planned extraction candidate.
* ADR-002: Human-in-the-loop write gate — citations are what the analyst reviews and approves against.
* ADR-003: Model/provider gateway — `EmbedPort` is the only path to embeddings; version envelope shared.
* ADR-004: Durable workflows — retrieval and ingestion are idempotent activities.
* Spring AI ETL Pipeline; Spring AI PGvector; pgvector Documentation; OpenAI Retrieval Guide.
* `docs/data-classification.md`
* Week 26 deliverable: versioned ingestion service, metadata table, chunking rules, vector storage, retryable background job, source traceability, sample run report.

## 8. Review

**Review date:** 2026-12-25
**Review trigger:** Any of the following causes reconsideration:

* Retrieval quality evals  show versioning or metadata is insufficient for grounding.
* Corpus growth makes pgvector performance or storage cost unacceptable.
* A regulatory requirement changes retention or residency in a way the current layout cannot satisfy.
* A managed knowledge base becomes viable under classification constraints and changes the cost/ops calculus.
* Citation drift or stale-corpus incidents occur in production.
* Deduplication proves insufficient and retrieval pollution degrades answer quality.

**Status history:**

* 2026-09-25 — Proposed
* 2026-09-25 — Accepted
