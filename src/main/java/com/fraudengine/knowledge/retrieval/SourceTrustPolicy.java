/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/retrieval/SourceTrustPolicy.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Check exact source provenance before indexing or exposing retrieved source text.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - An exact source revision/checksum needs explicit curator trust; ingestion cannot confer trust.
 *   - All eligible scoped passages are checked before query inference. Mixed untrusted corpora fail closed.
 *   - Trust indicates provenance review, not that source instructions become authoritative.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;

import com.fraudengine.knowledge.api.dto.RetrievalResult;
import com.fraudengine.platform.api.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SourceTrustPolicy {
    private final JdbcTemplate jdbc;
    private final boolean required;

    public SourceTrustPolicy(JdbcTemplate jdbc, @Value("${knowledge.trust.required:true}") boolean required) {
        this.jdbc = jdbc;
        this.required = required;
    }

    public void requireTrusted(List<RetrievalResult.Passage> passages) {
        if (!required)
            return;
        for (var passage : passages) {
            var citation = passage.citation();
            if ("C3".equals(citation.classification())
                    || !com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(passage.text())
                            .equals(citation.chunkHash())
                    || !Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM knowledge.trusted_sources WHERE source_id=? AND source_version=? AND checksum=? AND revoked=false)",
                            Boolean.class,
                            citation.sourceId(), citation.sourceVersion(), citation.sourceChecksum())))
                throw new AccessDeniedException();
        }
    }
}
