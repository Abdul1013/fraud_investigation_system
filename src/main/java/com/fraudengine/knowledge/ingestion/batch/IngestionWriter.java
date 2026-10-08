package com.fraudengine.knowledge.ingestion.batch;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.batch.item.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Source lock + immutable registry + vector/lineage inserts share the Batch transaction. */
public class IngestionWriter implements ItemWriter<DocumentBatch> {
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final long jobInstanceId;
    private final String provider;
    public IngestionWriter(VectorStore vectorStore, JdbcTemplate jdbc, long jobInstanceId, String provider) {
        this.vectorStore = vectorStore; this.jdbc = jdbc; this.jobInstanceId = jobInstanceId; this.provider = provider;
    }
    @Override public void write(Chunk<? extends DocumentBatch> items) {
        for (var batch : items) {
            if (!provider.equals("local") && batch.classification().equals("C2"))
                throw new IllegalArgumentException("C2 external embedding requires a reviewed redaction gateway");
            String identity = batch.sourceId() + ":" + batch.sourceVersion();
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "knowledge-source:" + identity);
            UUID documentId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
            var existing = jdbc.queryForList("SELECT checksum FROM knowledge.document_registry WHERE id = ?", String.class, documentId);
            int created = 0, duplicate = 0;
            if (!existing.isEmpty()) {
                if (!existing.getFirst().equals(batch.checksum()))
                    throw new IllegalArgumentException("Source revision changed; increment source_version: " + identity);
                duplicate = jdbc.queryForObject("SELECT chunk_count FROM knowledge.document_registry WHERE id = ?", Integer.class, documentId);
            } else {
                if ((batch.classification().equals("C3") || batch.docType().equals("kyc")) && !batch.documents().isEmpty())
                    throw new IllegalArgumentException("Restricted text reached writer");
                jdbc.update("""
                        INSERT INTO knowledge.document_registry
                        (id,source_id,source_version,doc_type,classification,checksum,content_hash,chunk_count,ingested_by,raw_uri,effective_from)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        """, documentId, batch.sourceId(), batch.sourceVersion(), batch.docType(), batch.classification(),
                        batch.checksum(), batch.checksum(), batch.documents().size(), "knowledgeIngestionJob", batch.rawUri(),
                        batch.documents().isEmpty() || batch.documents().getFirst().getMetadata().get("effective_from") == null ? null :
                        java.sql.Timestamp.from(java.time.LocalDate.parse(batch.documents().getFirst().getMetadata().get("effective_from").toString())
                                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
                if (!batch.documents().isEmpty()) vectorStore.add(batch.documents());
                for (var doc : batch.documents()) {
                    UUID chunkId = UUID.fromString(doc.getId());
                    jdbc.update("INSERT INTO knowledge.chunks_lineage(id,document_id,chunk_index,chunk_hash,vector_id) VALUES (?,?,?,?,?)",
                            chunkId, documentId, doc.getMetadata().get("chunk_index"), doc.getMetadata().get("chunk_hash"), chunkId);
                }
                created = batch.documents().size();
            }
            jdbc.update("""
                    INSERT INTO knowledge.ingestion_sources(job_instance_id,document_id,chunks_created,chunks_skipped_duplicate)
                    VALUES (?,?,?,?) ON CONFLICT DO NOTHING
                    """, jobInstanceId, documentId, created, duplicate);
        }
    }
}
