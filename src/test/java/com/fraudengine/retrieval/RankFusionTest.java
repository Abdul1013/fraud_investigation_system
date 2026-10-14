/**
 * WEEK 27 — Fusion tests check meaningful ranking behavior: evidence supported by both signals
 * wins, and a keyword score hundreds of times larger cannot dominate simply because it uses another
 * scale. Vector validation tests prevent undefined cosine distances from contaminating rankings.
 */
package com.fraudengine.retrieval;

import static org.assertj.core.api.Assertions.*;

import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.knowledge.retrieval.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class RankFusionTest {
    RetrievalResult.Passage hit(UUID id, double score) {
        return new RetrievalResult.Passage(
                "passage", score, new Citation(id, "source", 1, "hash", "chunk", "C0"));
    }

    @Test
    void agreementOutranksSingleSignalAndRawScoreMagnitudeDoesNotDominate() {
        UUID semantic = UUID.randomUUID(), lexical = UUID.randomUUID(), both = UUID.randomUUID();
        var result =
                ReciprocalRankFusion.merge(
                        List.of(hit(semantic, 0.9), hit(both, 0.8)),
                        List.of(hit(lexical, 500), hit(both, 0.01)),
                        3);
        assertThat(result.getFirst().citation().chunkId()).isEqualTo(both);
        assertThat(result).extracting(p -> p.citation().chunkId()).doesNotHaveDuplicates();
        assertThat(ReciprocalRankFusion.merge(List.of(), List.of(), 5)).isEmpty();
    }

    @Test
    void vectorValidationRejectsUndefinedOrNonFiniteDistances() {
        assertThatThrownBy(() -> RetrievalRepository.vectorLiteral(new float[] {0, 0}))
                .isInstanceOf(IndexUnavailableException.class);
        assertThatThrownBy(() -> RetrievalRepository.vectorLiteral(new float[] {Float.NaN}))
                .isInstanceOf(IndexUnavailableException.class);
    }
}
