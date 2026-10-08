package com.fraudengine.knowledge.ingestion.transformer;

import org.springframework.ai.document.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

/**
 * Optional chunk filter. The writer also locks source revisions to prevent
 * concurrent duplicates.
 */
public class DeduplicationTransformer implements DocumentTransformer {
    private final JdbcTemplate jdbc;
    private final String sourceId;
    private final int sourceVersion;

    public DeduplicationTransformer(JdbcTemplate jdbc, String sourceId, int sourceVersion) {
        this.jdbc = jdbc;
        this.sourceId = sourceId;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        return documents.stream().filter(doc -> jdbc.queryForObject("""
                SELECT COUNT(*) FROM knowledge.chunks_lineage c
                JOIN knowledge.document_registry d ON d.id = c.document_id
                WHERE d.source_id = ? AND d.source_version = ? AND c.chunk_index = ? AND c.chunk_hash = ?
                """, Integer.class, sourceId, sourceVersion, doc.getMetadata().get("chunk_index"),
                doc.getMetadata().get("chunk_hash")) == 0).toList();
    }
}
