package com.fraudengine.knowledge.ingestion.batch;

import org.springframework.ai.document.Document;
import java.util.List;

/** One immutable source revision; documents become retrieval passages after processing. */
public record DocumentBatch(String sourceId, int sourceVersion, String docType,
        String classification, String checksum, String rawUri, List<Document> documents) {
    public DocumentBatch {
        documents = List.copyOf(documents);
    }
    public DocumentBatch withDocuments(List<Document> value) {
        return new DocumentBatch(sourceId, sourceVersion, docType, classification, checksum, rawUri, value);
    }
}
