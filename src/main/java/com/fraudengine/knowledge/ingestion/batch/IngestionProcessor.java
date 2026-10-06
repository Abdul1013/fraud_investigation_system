package com.fraudengine.knowledge.ingestion.batch;

import org.springframework.ai.document.Document;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class IngestionProcessor implements ItemProcessor<DocumentBatch, DocumentBatch> {

    private final MetadataEnricherTransformer enricher;
    private final StrucutreAwareChunker chunker;
    private final JdbcTemplate jdbcTemplate;

    public IngestionProcessor(JdbcTemplate jdbcTemplate) {
        this.enricher = new MetadataEnricherTransformer();
        this.chunker = new StructureAwareChunker();
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DocumentBatch process(DocumentBatch batch) throws Exception {
        // Implementation for processing each document batch

        //1. enrich metadata
        List<Document> enriched = enricher.apply(batch.documents());

        // 2. chunck ny doc_type
        List<Document> chunks = chunker.apply(enriched);

        //3. deduplicate  
        var deduplicated = new DeduplicationTransformer(jdbcTemplate, batch.sourceId(), batch.sourceVersion(), batch.docType(), unique);

    }

}
