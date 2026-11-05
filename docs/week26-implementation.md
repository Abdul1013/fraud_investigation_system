# Week 26: versioned knowledge ingestion

This module implements the curriculum's versioned ingestion service using Spring Batch, PostgreSQL, and pgvector. It covers metadata, source revisions, semantic text splitting, exact deduplication, retry, restart, source lineage, corpus publication, and truthful run reports.

## Reading the implementation

1. `IngestionBatchReader` discovers one Markdown file per source item in `policies`, `prior-cases`, `regulatory`, and `kyc`. Names ending in `-vN` declare revision N; other filenames use revision 1. Source identity includes its directory. Do not edit the content of an existing revision; add a new revision filename.
2. `SourceDocumentLoader` creates a checksum and an immutable content-addressed raw snapshot. Classification defaults to the source type and can be raised by a source declaration. C3 and KYC produce no model-facing documents.
3. `MetadataEnricherTransformer` validates the provenance contract. `StructureAwareChunker` preserves semantic headings, token-splits prose, and keeps table sections together. Oversized table sections are rejected and reported. Final chunk indices and SHA-256 hashes produce deterministic UUIDs.
4. `IngestionWriter` acquires a transaction-scoped PostgreSQL source lock, checks source immutability, and commits registry, vectors, lineage, and run membership together. Same revision/checksum is reused without embedding again. Different content under an existing revision is rejected.
5. `RunReportWriter` publishes only successful complete corpora. A snapshot contains the latest revision per source from the prior published snapshot and current successful run. Earlier snapshots retain earlier evidence. Failed and partial runs never publish a corpus.
6. `KnowledgeController` launches the job in a bounded background executor and exposes status, restart, and published corpus versions.

One source item commits per transaction. This is separate from text chunk sizing. Reader manifest and cursor are saved in Batch execution context. Restarting a failed instance resumes from its last committed source; changed manifests are rejected. A new run key creates a new job instance and tests re-ingestion instead of restart.

## Run and inspect

```bash
docker compose up -d postgres
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Use the HTTP examples in the README. Requests accept a run key, not a client-selected filesystem path. Configure the corpus on the server with `knowledge.corpus-root`. `POST /v1/knowledge/ingestions/{runId}/restart` accepts only failed/stopped runs. A completed/partial run needs a new key after correcting inputs.

Query the database with:

```bash
docker compose exec postgres psql -U fraud -d fraud -c "SELECT source_id,source_version,classification,chunk_count FROM knowledge.document_registry ORDER BY source_id;"
docker compose exec postgres psql -U fraud -d fraud -c "SELECT run_id,status,documents_processed,chunks_created,chunks_skipped_duplicate FROM knowledge.ingestion_runs ORDER BY run_id;"
```

`documentsProcessed` counts persisted source revisions for the job instance. `chunksCreated` and `chunksSkippedDuplicate` count actual text chunks, not Batch items. Restart reports include the job instance's previously committed sources. Traceability keys include both source identity and revision. Each entry includes classification, checksum, recoverable raw URI, and chunk IDs. Restricted sources have an empty chunk-ID list. `runId` is the Batch execution ID, so failures and their resumed attempts have separate evidence.

`PARTIAL` means at least one deterministic source failure was skipped; the Batch execution can complete while the report says partial. No corpus is published in that case. Temporary database/embedding errors retry up to three attempts with backoff. Exhausted temporary failures fail the job and can be restarted.

## Verification

The integration suite uses a fresh `pgvector/pgvector:pg16` Testcontainer and real Flyway migrations and SQL. It uses the local embedding fixture, not a paid provider. Tests are not silently skipped if Docker is unavailable.

Coverage includes complete ingestion, restricted-source registration, C3 exclusion for a non-KYC document, exact duplicate ingestion without model calls, new source versions and old corpus membership, transient retry, failed-instance restart, malformed and empty-source reporting, changed-revision rejection, manifest change rejection, and the bundled sample corpus. Unit tests cover provenance validation, structure/table preservation, unique indices and deterministic IDs, dedup filtering, and report time serialization.

Testcontainers 1.21.3 is managed by Boot; `src/test/resources/docker-java.properties` selects API 1.44 for Docker Engine 29 compatibility. Tests run under the normal `mvn test` lifecycle. CI uses Java 21; this workspace currently has Java 25 installed and the Maven compiler targets Java 21.

Sample operational evidence is exported by the bundled-corpus integration test to `target/test-reports/sample-ingestion-run.json`. Checked-in evidence: [sample report](evidence/week26/sample-ingestion-run.json) and [verification results](evidence/week26/verification.json).

Verified on 7 October 2026: `mvn -q verify` passed with 21 tests, 0 failures/errors/skips (12 integration tests), and `docker compose config --quiet` passed. The bundled corpus produced 11 source revisions and 42 text chunks. HTTP tests confirmed background launch, status/report retrieval, conflict handling, invalid input rejection, corpus listing, and health UP. Concurrent runs preserved unique source/vector/lineage rows.

## Schema ownership and migration scope

Flyway owns application DDL. The originally broken V2 initial migration was corrected; later placeholder migrations are unchanged. Verification uses fresh disposable databases. No existing user database has been reset or repaired.

If your own database has an applied V2 from another checkout, its Flyway checksum/schema can differ. Inspect its migration history and back it up before planning an additive migration. Do not run `flyway repair` or delete a persistent volume just to bypass a mismatch.

## Deliberate limits

- Embedding spaces must not be mixed. Switching from the local fixture to a semantic model, or changing model identity, requires a separate vector table/database and a full re-ingestion. Existing source revisions otherwise reuse their stored vectors; provider/model migration is not implemented here.
- Default embeddings are local token-hashing fixtures. They prove ingestion plumbing and avoid external transmission; they do not satisfy retrieval-quality claims. Semantic embeddings and retrieval evaluation belong to Week 27.
- An OpenAI provider can be selected for reviewed public/internal sources with `EMBEDDING_PROVIDER=openai`, `SPRING_AI_MODEL_EMBEDDING=openai`, and `OPENAI_API_KEY`. This path has not been verified against the paid API. External C2 ingestion is blocked until a reviewed redaction gateway exists. C3 never enters embedding input.
- Classification declarations are trusted ingestion metadata, with source-type defaults acting as a floor. This is not automated field-level PII classification. Use synthetic data for learning.
- Exact deduplication is implemented. Similarity-based near-duplicate detection remains a later enhancement and must preserve legitimate source lineage.
- Source parsing currently supports Markdown. PDF dependencies remain available, but no PDF ingestion path is claimed. Table sections over the configured bound are skipped and reported rather than split incorrectly.
- Raw snapshots use restricted local filesystem permissions on POSIX systems. Encryption, retention, object storage, authentication/authorization, and production access controls remain later work. The local API binds to loopback and is intended for development.
- Background execution is bounded in one application process. Batch metadata supports explicit restart; automatic reclamation of executions left STARTED by a hard process kill is not implemented. Tests demonstrate failure/restart after retry exhaustion.
- Chunk transactions cannot undo an external embedding request or its cost. Deterministic IDs and source locks prevent duplicate database effects; they do not guarantee zero repeated provider charges.
- Report serialization/database publication failures fail the reporting step. Failed disk export is logged; persisted database JSON remains authoritative and can be exported again.

## Learning checkpoints

Explain why a failed second source leaves the first committed, why a restarted report includes both executions' source progress, why duplicate count differs from write count, and why a C3 policy has registry evidence but zero vectors. Then inspect the tests to find the assertion that proves each behavior.



`InjestionJobConfig.java`, `CaseDocumentReader.java`, `VectorStoreWriter.java`, `RetryPolicyConfig.java`, `SkipPolicyConfig.java`, `PgVectorStoreConfig.java`, and `IngestionJobIT.java` are legacy or placeholder files. The active implementations are listed above. Likewise, use `knowledge/ingestion/report/IngestionRunReport.java` for the working report—not the similarly named API DTO scaffold.

Start reading with  **`IngestionJobConfig → IngestionBatchReader → IngestionProcessor → IngestionWriter → RunReportWriter`** .
