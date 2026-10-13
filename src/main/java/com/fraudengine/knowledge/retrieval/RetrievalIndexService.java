/**
 * WEEK 27 — Explicit, bounded re-embedding of a published corpus into a separate model space.
 * Network inference happens outside each short database transaction. Immutable chunks and a
 * composite unique key make retries idempotent. This keeps Week 26 ingestion working and lets you
 * compare semantic models without overwriting historical vectors. Large-scale/durable model
 * migration is deliberately a later job; this synchronous learning endpoint is bounded to 1000
 * chunks and reports which provider was used.
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
            int reused) {}

    private final RetrievalRepository repository;
    private final RetrievalEmbeddingPort embeddings;
    private final RetrievalService retrieval;
    private final TransactionTemplate transaction;

    public RetrievalIndexService(
            RetrievalRepository repository,
            RetrievalEmbeddingPort embeddings,
            RetrievalService retrieval,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.embeddings = embeddings;
        this.retrieval = retrieval;
        this.transaction = new TransactionTemplate(transactions);
    }

    public IndexReport index(Integer requested) {
        int corpus = repository.resolveCorpus(requested);
        String space = embeddings.spaceId();
        var passages = repository.eligible(corpus, retrieval.allowedClassifications());
        if (passages.size() > 1000)
            throw new IndexUnavailableException(
                    "Index request exceeds 1000 chunks; use a background indexing job");
        int created = 0, reused = 0;
        for (var passage : passages) {
            if (repository.indexed(passage.citation().chunkId(), space)) {
                reused++;
                continue;
            }
            float[] vector = embeddings.embed(passage.text());
            RetrievalRepository.vectorLiteral(vector);
            if (!space.equals(embeddings.spaceId()))
                throw new IndexUnavailableException(
                        "Model changed while indexing; retry with the new space");
            boolean inserted =
                    Boolean.TRUE.equals(
                            transaction.execute(
                                    status ->
                                            repository.store(
                                                    passage.citation().chunkId(),
                                                    space,
                                                    embeddings.fixture(),
                                                    vector)));
            if (inserted) created++;
            else reused++;
        }
        return new IndexReport(corpus, space, embeddings.fixture(), created, reused);
    }
}
