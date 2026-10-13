/**
Application service resolves the corpus and chooses VECTOR or HYBRID. One
 * server-controlled development clearance limits both paths; a future authenticated identity must
 * supply per-caller clearance. We reject incomplete model indexes so model changes cannot silently
 * mix old vectors. The similarity threshold controls vector candidate admission, not fraud
 * confidence. Its value is versioned in every result, and hybrid uses the same candidates plus
 * PostgreSQL lexical evidence.
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
            throw new IllegalArgumentException("Retrieval ceiling must be C0..C2");
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
        int corpus = repository.resolveCorpus(request.corpusVersion());
        String space = embeddings.spaceId();
        repository.requireComplete(
                corpus, request.docType(), RetrievalRepository.date(request), allowed, space);
        float[] query = embeddings.embed(request.query());
        repository.requireDimensions(space, query.length);
        if (!space.equals(embeddings.spaceId()))
            throw new IndexUnavailableException("Model changed during retrieval");
        int candidates = Math.min(100, Math.max(20, request.limit() * 4));
        var vector = repository.vector(
                corpus, request, allowed, space, query, candidates, minimumSimilarity);
        var passages = request.selectedStrategy() == RetrievalRequest.Strategy.VECTOR
                ? vector.stream().limit(request.limit()).toList()
                : ReciprocalRankFusion.merge(
                        vector,
                        repository.keyword(corpus, request, allowed, candidates),
                        request.limit());
        return new RetrievalResult(
                corpus,
                "week27-v1:min=" + minimumSimilarity + ":candidates=" + candidates + ":rrf=60",
                space,
                embeddings.fixture(),
                request.selectedStrategy().name(),
                passages);
    }
}
