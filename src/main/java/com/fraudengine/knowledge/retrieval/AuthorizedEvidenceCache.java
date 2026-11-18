/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/retrieval/AuthorizedEvidenceCache.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Cache only eligible evidence under an exact authorized subject/scope/version identity.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Query text is hashed; credentials, OTP data, execution authority and degraded reads are never cache values.
 *   - Authorization runs before lookup and result delivery. Database triggers automatically change namespace revisions.
 *   - A byte-weight budget plus a minimum per-entry weight bounds both memory and entry count.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;

import com.github.benmanes.caffeine.cache.*;
import com.fraudengine.cases.api.dto.CaseEvidenceScope;
import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.platform.api.CurrentCaller;
import com.fraudengine.platform.gateway.RetrievalEmbeddingPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
public class AuthorizedEvidenceCache {
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)\\b(otp|password|secret|bearer)\\b|approval[ _-]?token|execution[ _-]?authori|\\b[0-9]{6}\\b|\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");

    record Key(String subject, String caseId, String ceiling, Set<String> sources, long grantRevision,
            long sourceRevision,
            int corpus, String model, String retrieval, String strategy, int limit, String docType,
            java.time.LocalDate asOf, String queryHash) {
    }

    private final Cache<Key, RetrievalResult> cache;
    private final boolean enabled;
    private final long maximumBytes;
    private final int minimumWeight;
    private final CurrentCaller caller;
    private final RetrievalEmbeddingPort embeddings;
    private final RetrievalRepository repository;
    private final SourceTrustPolicy trust;
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;

    public AuthorizedEvidenceCache(CurrentCaller caller, RetrievalEmbeddingPort embeddings,
            RetrievalRepository repository,
            SourceTrustPolicy trust, JdbcTemplate jdbc, MeterRegistry meters,
            @Value("${knowledge.cache.enabled:true}") boolean enabled,
            @Value("${knowledge.cache.maximum-entries:256}") int maximumEntries,
            @Value("${knowledge.cache.maximum-bytes:16777216}") long maximumBytes,
            @Value("${knowledge.cache.ttl-seconds:30}") int ttl) {
        if (maximumEntries < 1 || maximumEntries > 1024 || maximumBytes < 65536 || maximumBytes > 134217728 || ttl < 1
                || ttl > 300)
            throw new IllegalArgumentException("Invalid bounded cache policy");
        this.enabled = enabled;
        this.maximumBytes = maximumBytes;
        minimumWeight = (int) Math.ceil(maximumBytes / (double) maximumEntries);
        this.caller = caller;
        this.embeddings = embeddings;
        this.repository = repository;
        this.trust = trust;
        this.jdbc = jdbc;
        this.meters = meters;
        cache = Caffeine.newBuilder().maximumWeight(maximumBytes)
                .weigher((Key key, RetrievalResult value) -> weight(key, value))
                .expireAfterWrite(Duration.ofSeconds(ttl)).recordStats().build();
    }

    public RetrievalResult read(CaseEvidenceScope scope, RetrievalRequest request, String retrievalVersion,
            Supplier<RetrievalResult> load) {
        if (!enabled || scope.sourceIds().isEmpty() || CREDENTIAL.matcher(request.query()).find()) {
            count("bypass");
            return load.get();
        }
        String subject = caller.requireAuthenticated().subject();
        int corpus = repository.resolveCorpus(request.corpusVersion());
        long revision = jdbc.queryForObject("SELECT revision FROM knowledge.scope_revision WHERE singleton=true",
                Long.class);
        String space = embeddings.spaceId();
        var key = new Key(subject, scope.caseId(), scope.classificationCeiling(), scope.sourceIds(), scope.revision(),
                revision, corpus, space,
                retrievalVersion, request.selectedStrategy().name(), request.limit(), request.docType(),
                RetrievalRepository.date(request),
                com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(request.query()));
        var found = cache.getIfPresent(key);
        if (found != null) {
            trust.requireTrusted(found.passages());
            count("hit");
            return found;
        }
        count("miss");
        var loaded = load.get();
        boolean eligible = !loaded.degraded() && !loaded.passages().isEmpty() && loaded.passages().stream()
                .allMatch(p -> !"C3".equals(p.citation().classification()) && !CREDENTIAL.matcher(p.text()).find());
        if (eligible && loaded.corpusVersion() == corpus && loaded.embeddingSpace().equals(space)
                && loaded.retrievalConfigVersion().equals(retrievalVersion)
                && weight(key, loaded) <= maximumBytes)
            cache.put(key, loaded);
        return loaded;
    }

    private int weight(Key key, RetrievalResult value) {
        long bytes = 2048 + key.sources().stream().mapToLong(source -> 64 + source.length() * 2L).sum()
                + value.passages().stream().mapToLong(p -> 512 + p.text().length() * 2L).sum();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(minimumWeight, bytes));
    }

    private void count(String outcome) {
        meters.counter("fraud.retrieval.cache", "outcome", outcome).increment();
    }

    void clear() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    public long estimatedSize() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
