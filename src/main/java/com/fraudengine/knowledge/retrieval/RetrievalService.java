/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/retrieval/RetrievalService.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Retrieve ranked evidence within the server and trusted case scope.
 * ----------------------------------------------------------------------------
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-27
 * RELATED:     ADR-005, Week 28 case-scoped evidence
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Caller clearance intersects the server ceiling; neither may widen the other.
 *   - Source restrictions reach SQL before ranking or text disclosure to a model.
 *   - Empty case scope returns no passages without embedding the query.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;

import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.platform.gateway.RetrievalEmbeddingPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RetrievalService {
    private final RetrievalRepository repository;
    private final RetrievalEmbeddingPort embeddings;
    private final List<String> allowed;
    private final double minimumSimilarity;

    public RetrievalService(
            RetrievalRepository repository,
            RetrievalEmbeddingPort embeddings,
            @Value("${knowledge.retrieval.classification-ceiling:C2}") String ceiling,
            @Value("${knowledge.retrieval.minimum-similarity:0.15}") double minimum) {
        if (!List.of("C0", "C1", "C2").contains(ceiling))
            throw new IllegalArgumentException("Retrieval ceiling must be C0 to C2");
        if (!Double.isFinite(minimum) || minimum < 0 || minimum > 1)
            throw new IllegalArgumentException("Invalid similarity threshold");
        this.repository = repository;
        this.embeddings = embeddings;
        this.minimumSimilarity = minimum;
        allowed = List.of("C0", "C1", "C2").stream().filter(c -> c.compareTo(ceiling) <= 0).toList();
    }

    public List<String> allowedClassifications() {
        return allowed;
    }

    public RetrievalResult retrieve(RetrievalRequest request) {
        return retrieveWithin(request, allowed, null);
    }

    public RetrievalResult retrieveScoped(RetrievalRequest request, String ceiling, Set<String> sourceIds) {
        Objects.requireNonNull(sourceIds, "case source scope");
        if (!List.of("C0", "C1", "C2").contains(ceiling)) {
            throw new IllegalArgumentException("Case clearance must be C0 to C2");
        }
        var intersection = allowed.stream().filter(value -> value.compareTo(ceiling) <= 0).toList();
        return retrieveWithin(request, intersection, Set.copyOf(sourceIds));
    }

    private RetrievalResult retrieveWithin(RetrievalRequest request, List<String> permitted, Set<String> sourceIds) {
        int corpus = repository.resolveCorpus(request.corpusVersion());
        if (sourceIds != null && sourceIds.isEmpty()) {
            return new RetrievalResult(corpus, "week28-scoped-v1:empty", "not-invoked", embeddings.fixture(),
                    request.selectedStrategy().name(), List.of());
        }
        String space = embeddings.spaceId();
        repository.requireComplete(
                corpus, request.docType(), RetrievalRepository.date(request), permitted, space, sourceIds);
        float[] query = embeddings.embed(request.query());
        repository.requireDimensions(space, query.length);
        if (!space.equals(embeddings.spaceId()))
            throw new IndexUnavailableException("Model changed during retrieval");
        int candidates = Math.min(100, Math.max(20, request.limit() * 4));
        var vector = repository.vector(
                corpus, request, permitted, space, query, candidates, minimumSimilarity, sourceIds);
        var passages = request.selectedStrategy() == RetrievalRequest.Strategy.VECTOR
                ? vector.stream().limit(request.limit()).toList()
                : ReciprocalRankFusion.merge(
                        vector,
                        repository.keyword(corpus, request, permitted, candidates, sourceIds),
                        request.limit());
        return new RetrievalResult(
                corpus,
                (sourceIds == null ? "week27-v1:min=" : "week28-scoped-v1:min=") + minimumSimilarity + ":candidates=" + candidates + ":rrf=60",
                space,
                embeddings.fixture(),
                request.selectedStrategy().name(),
                passages);
    }
}
