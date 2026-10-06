/**

 * FILE:        src/main/java/com/fraudengine/knowledge/ingestion/transformer/MetadataEnricherTransformer.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Reserves batch transformation for document provenance metadata.
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-2
 * RELATED:     ADR-005
 * NOTES:
 *   - TODO (week-2): populate required source-version metadata.
 *   - Transformation behavior is intentionally omitted.

 */
package com.fraudengine.knowledge.ingestion.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** ensures every chunck carries the 
 * full provennace envelope required by ADR-005 */

public class MetadataEnricherTransformer  implements  DocumentTransformer{
    @Override 
    public List <Document> apply(Liist<Document> documents) {
        return documents.stream()
        .map(this::enrich)
        .collect(Collectors.toList());
    }

    private Document enrich(Document doc){
    var meta = doc.getMetadata();

    requireKey(meta, "source_id");
    requireKey(meta, "source_version");
    requireKey(meta, "doc_type");
    requireKey(meta, "classification");
    requireKey(meta, "content-hash");

    meta.put("ingested_at", java.time.Instant.now().toString());
    meta.put("pipeline_version", "week 2");

    return new Document(doc.getId(), doc.getText(), meta);
}

private void requireKey(Map<String, Object> meta, String key){
    if( !meta.containsKey(key) || meta.get(key) == null ){
        throw new IllegalArgumentException("Document missing required metadata: " + key + "- cannot proceed with ingestion");
    }
}
}

