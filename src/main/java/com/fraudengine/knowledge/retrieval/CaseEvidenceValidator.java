/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/retrieval/CaseEvidenceValidator.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Reauthorize evidence delivery and decisions in a short database snapshot after model work.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - No model call occurs inside this transaction; revocation during slow work is checked before returning data.
 *   - Human decisions additionally require nonempty, non-degraded, still-trusted evidence.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;

import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.knowledge.api.dto.RetrievalResult;
import com.fraudengine.platform.api.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CaseEvidenceValidator {
    private final CaseApi cases;
    private final RetrievalService retrieval;
    private final RetrievalRepository repository;
    private final SourceTrustPolicy trust;

    public CaseEvidenceValidator(CaseApi cases, RetrievalService retrieval, RetrievalRepository repository,
            SourceTrustPolicy trust) {
        this.cases = cases;
        this.retrieval = retrieval;
        this.repository = repository;
        this.trust = trust;
    }

    public void validateRead(String caseId, RetrievalResult evidence) {
        validate(caseId, evidence, false);
    }

    public void validateDecision(String caseId, RetrievalResult evidence) {
        validate(caseId, evidence, true);
    }

    private void validate(String caseId, RetrievalResult evidence, boolean decision) {
        var scope = cases.evidenceScope(caseId);
        if (evidence == null || evidence.passages() == null || evidence.passages().size() > 20
                || (decision && (evidence.passages().isEmpty() || evidence.degraded())))
            throw new AccessDeniedException();
        var allowed = retrieval.allowedClassifications().stream()
                .filter(c -> c.compareTo(scope.classificationCeiling()) <= 0).toList();
        repository.resolveCorpus(evidence.corpusVersion());
        var eligible = repository.eligibleScoped(evidence.corpusVersion(), allowed, scope.sourceIds());
        if (evidence.passages().stream().anyMatch(p -> p == null || eligible.stream()
                .noneMatch(stored -> stored.citation().equals(p.citation()) && stored.text().equals(p.text()))))
            throw new AccessDeniedException();
        trust.requireTrusted(evidence.passages());
    }
}
