/**
 * WEEK 27 — Return ranked evidence separately from generated answers. The version envelope records
 * corpus, retrieval algorithm, and embedding space to make comparisons reproducible. Scores are
 * strategy-specific: cosine similarity for VECTOR and reciprocal-rank fusion for HYBRID; consumers
 * must not interpret either as the probability that a fraud claim is true.
 */
package com.fraudengine.knowledge.api.dto;

import java.util.List;

public record RetrievalResult(
        int corpusVersion,
        String retrievalConfigVersion,
        String embeddingSpace,
        boolean fixtureEmbeddings,
        String strategy,
        List<Passage> passages, boolean degraded, String degradationReason) {
    public RetrievalResult(int corpusVersion, String retrievalConfigVersion, String embeddingSpace,
            boolean fixtureEmbeddings, String strategy, List<Passage> passages) {
        this(corpusVersion, retrievalConfigVersion, embeddingSpace, fixtureEmbeddings, strategy, passages, false, null);
    }

    public RetrievalResult asDegraded(String reason) {
        return new RetrievalResult(corpusVersion, retrievalConfigVersion, embeddingSpace, fixtureEmbeddings, strategy,
                passages, true, reason);
    }

    public record Passage(String text, double score, Citation citation) {
    }
}
