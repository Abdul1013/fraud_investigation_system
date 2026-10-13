/**
 * Combine vector and keyword ranks using 1/(60+rank), then deduplicate by chunk identity.
 * Keyword rank and cosine similarity have different scales; averaging their raw scores would be
 * misleading. Rank fusion rewards passages supported by both signals without requiring a learned
 * reranker. Stable UUID ordering breaks ties reproducibly, which is necessary for fair evaluation
 * comparisons.
 */
package com.fraudengine.knowledge.retrieval;

import com.fraudengine.knowledge.api.dto.RetrievalResult.Passage;

import java.util.*;

public final class ReciprocalRankFusion {
    private ReciprocalRankFusion() {
    }

    public static List<Passage> merge(List<Passage> vector, List<Passage> keyword, int limit) {
        Map<UUID, Passage> passages = new HashMap<>();
        Map<UUID, Double> scores = new HashMap<>();
        for (var list : List.of(vector, keyword))
            for (int rank = 0; rank < list.size(); rank++) {
                var passage = list.get(rank);
                var id = passage.citation().chunkId();
                passages.putIfAbsent(id, passage);
                scores.merge(id, 1.0 / (60 + rank + 1), Double::sum);
            }
        return passages.values().stream()
                .map(p -> new Passage(p.text(), scores.get(p.citation().chunkId()), p.citation()))
                .sorted(
                        Comparator.comparingDouble(Passage::score)
                                .reversed()
                                .thenComparing(p -> p.citation().chunkId()))
                .limit(limit)
                .toList();
    }
}
