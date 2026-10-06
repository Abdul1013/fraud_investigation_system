/**
 * FILE:        src/main/java/com/fraudengine/knowledge/domain/DocumentMetadata.java
 * CONTEXT:     knowledge
 * LAYER:       domain
 * PURPOSE:     Names required provenance metadata for a source document version.
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-2
 * RELATED:     ADR-005
 * NOTES:
 *   - TODO: define source_id, source_version, effective_from, classification, checksum, and ingested_at.
 *   - Include doc_type, jurisdiction, and product_line when those contracts are settled.
 */
package com.fraudengine.knowledge.domain;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public record DocumentMetadata(
    String sourceId,
    int sourceVersion,
    String docType,
    String classification,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String contentHash,
    String checksum,
    String sourcePath
) {
    public Map<String, Object> toMetadataMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("source_id", sourceId);
        map.put("source_version", sourceVersion);
        map.put("doc_type", docType);
        map.put("classification", classification);
        if (effectiveFrom != null) {
            map.put("effective_from", effectiveFrom.toString());
        }
        if (effectiveTo != null) {
            map.put("effective_to", effectiveTo.toString());
        }
        map.put("content_hash", contentHash);
        map.put("checksum", checksum);
        map.put("source_path", sourcePath);
        return map;
    }
}

