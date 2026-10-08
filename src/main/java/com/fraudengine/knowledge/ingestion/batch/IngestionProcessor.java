package com.fraudengine.knowledge.ingestion.batch;

import com.fraudengine.knowledge.ingestion.transformer.*;
import org.springframework.batch.item.ItemProcessor;

/** Pure transformation; transactional deduplication belongs to the writer. */
public class IngestionProcessor implements ItemProcessor<DocumentBatch, DocumentBatch> {
    private final MetadataEnricherTransformer enricher = new MetadataEnricherTransformer();
    private final StructureAwareChunker chunker = new StructureAwareChunker();

    @Override
    public DocumentBatch process(DocumentBatch batch) {
        if (batch.classification().equals("C3") || batch.docType().equals("kyc"))
            return batch.withDocuments(java.util.List.of());
        return batch.withDocuments(chunker.apply(enricher.apply(batch.documents())));
    }
}
