/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/retrieval/RetrievalIndexService.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Index only provenance-approved immutable chunks into a separate model space.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-27
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Source trust is checked before embedding. Model dimension/space checks and idempotent writes remain authoritative.
 *   - Inference stays outside short database transactions; fixture results remain explicitly labelled.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;

import com.fraudengine.platform.gateway.RetrievalEmbeddingPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RetrievalIndexService {
    public record IndexReport(
            int corpusVersion,
            String embeddingSpace,
            boolean fixtureEmbeddings,
            int created,
            int reused) {
    }

    private final RetrievalRepository repository;
    private final RetrievalEmbeddingPort embeddings;
    private final RetrievalService retrieval;
    private final TransactionTemplate transaction;
    private final SourceTrustPolicy trust;

    public RetrievalIndexService(
            RetrievalRepository repository,
            RetrievalEmbeddingPort embeddings,
            RetrievalService retrieval,
            PlatformTransactionManager transactions,
            SourceTrustPolicy trust) {
        this.trust = trust;
        this.repository = repository;
        this.embeddings = embeddings;
        this.retrieval = retrieval;
        this.transaction = new TransactionTemplate(transactions);
    }

    private final java.util.concurrent.Semaphore indexing = new java.util.concurrent.Semaphore(1);

    public IndexReport index(Integer requested) {
        if (!indexing.tryAcquire())
            throw new com.fraudengine.platform.api.CapacityRejectedException(1);
        try {
            return indexBounded(requested);
        } finally {
            indexing.release();
        }
    }

    private IndexReport indexBounded(Integer requested) {
        int corpus = repository.resolveCorpus(requested);
        String space = embeddings.spaceId();
        var passages = repository.eligible(corpus, retrieval.allowedClassifications());
        trust.requireTrusted(passages);
        if (passages.size() > 1000)
            throw new IndexUnavailableException(
                    "Index request exceeds 1000 chunks; use a background indexing job");
        var missing = passages.stream().filter(p -> !repository.indexed(p.citation().chunkId(), space)).toList();
        int created = 0, 
        reused = passages.size() - missing.size();
        for (int offset = 0; offset < missing.size(); offset += 32) {
            var batch = missing.subList(offset, Math.min(offset + 32, missing.size()));
            var vectors = embeddings.embedBatch(batch.stream().map(p -> p.text()).toList());
            if (vectors.size() != batch.size() || !space.equals(embeddings.spaceId()))
                throw new IndexUnavailableException("Embedding batch changed count or model space");
            int dimension = vectors.getFirst().length;
            for (var vector : vectors) {
                RetrievalRepository.vectorLiteral(vector);
                if (vector.length != dimension)
                    throw new IndexUnavailableException("Embedding batch dimension changed");
            }
            // Recheck provenance after inference; short writes never surround network I/O.
            trust.requireTrusted(batch);
            for (int i = 0; i < batch.size(); i++) {
                var passage = batch.get(i);
                var vector = vectors.get(i);
                boolean inserted = Boolean.TRUE.equals(transaction.execute(status -> repository.store(
                        passage.citation().chunkId(), space, embeddings.fixture(), vector)));
                if (inserted)
                    created++;
                else
                    reused++;
            }
        }
        return new IndexReport(corpus, space, embeddings.fixture(), created, reused);
    }
}
