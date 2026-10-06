package com.fraudengine.knowledge.ingestion.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;


public class DeduplicationTransformer  implements DocumentTransformer{
    
    private final JdbcTemplate jdbcTemplate;
    private final String sourceId;
    private final int sourceVersion;

    public DeduplicationTransformer(JdbcTemplate jdbcTemplate, String sourceId, int sourceVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.sourceId = sourceId;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> unique = new ArrayList<>();
        for (Document doc : documents) {
            String hash = doc.getMetadata().get("content_hash");
            if (hash == null) {
                unique.add(doc);
                continue; 
            }

            Integer count = jdcbTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM chunk_lineage
                WHERE content_hash = ? AND source_version = ? AND chunk_hash = ?
                """,
                Integer.class, hash, sourceId, sourceVersion
            );

            if(count != null && count > 0) {
                continue; // Duplicate found, skip this document
            }
            unique.add(doc);
        }
        return unique;
    }

}
