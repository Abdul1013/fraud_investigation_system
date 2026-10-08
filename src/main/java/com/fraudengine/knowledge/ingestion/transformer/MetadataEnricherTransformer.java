package com.fraudengine.knowledge.ingestion.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import com.fraudengine.knowledge.domain.DocType;
import java.util.*;

/** Validates provenance before any embedding-capable stage. */
public class MetadataEnricherTransformer implements DocumentTransformer {
    @Override
    public List<Document> apply(List<Document> documents) {
        return documents.stream().map(doc -> {
            var meta = new HashMap<>(doc.getMetadata());
            for (String key : List.of("source_id", "source_version", "doc_type", "classification", "content_hash",
                    "source_path"))
                if (meta.get(key) == null || meta.get(key).toString().isBlank())
                    throw new IllegalArgumentException("Missing metadata: " + key);
            DocType.fromWire(meta.get("doc_type").toString());
            if (!Set.of("C0", "C1", "C2", "C3").contains(meta.get("classification")))
                throw new IllegalArgumentException("Invalid classification");
            if (!(meta.get("source_version") instanceof Number version) || version.intValue() < 1)
                throw new IllegalArgumentException("Invalid source version");
            meta.put("pipeline_version", "week-26-v1");
            return new Document(doc.getId(), doc.getText(), meta);
        }).toList();
    }
}
