/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/infrastructure/CaseEvidenceService.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Resolves case authorization before invoking the scoped retrieval engine.
 * ----------------------------------------------------------------------------
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Cross-context access uses only CaseApi; the knowledge context owns all retrieval SQL.
 *   - Validation applies to direct bean calls as well as HTTP so future tools cannot bypass bounds.
 *   - A read-only snapshot covers case grants and evidence reads; no model sees unrelated passage text.
 * ============================================================================
 */
package com.fraudengine.knowledge.infrastructure;

import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.RetrievalRequest;
import com.fraudengine.knowledge.api.dto.RetrievalResult;
import com.fraudengine.knowledge.retrieval.RetrievalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class CaseEvidenceService implements KnowledgeApi {
    private final CaseApi cases;
    private final RetrievalService retrieval;
    private final com.fraudengine.knowledge.retrieval.RetrievalRepository repository;

    public CaseEvidenceService(CaseApi cases, RetrievalService retrieval,
            com.fraudengine.knowledge.retrieval.RetrievalRepository repository) {
        this.cases = cases;
        this.retrieval = retrieval;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void validateCaseEvidence(String caseId, RetrievalResult evidence) {
        var scope = cases.evidenceScope(caseId);
        if (scope.sourceIds().isEmpty()) throw new com.fraudengine.platform.api.AccessDeniedException();
        var allowed = retrieval.allowedClassifications().stream()
                .filter(value -> value.compareTo(scope.classificationCeiling()) <= 0).toList();
        repository.resolveCorpus(evidence.corpusVersion());
        var eligible = repository.eligibleScoped(evidence.corpusVersion(), allowed, scope.sourceIds());
        if (evidence.passages() == null || evidence.passages().isEmpty() || evidence.passages().size() > 20
                || evidence.passages().stream().anyMatch(passage -> passage == null
                    || eligible.stream().noneMatch(stored -> stored.citation().equals(passage.citation())
                            && stored.text().equals(passage.text())))) {
            throw new com.fraudengine.platform.api.AccessDeniedException();
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RetrievalResult retrieveCaseEvidence(String caseId, RetrievalRequest request) {
        var scope = cases.evidenceScope(caseId);
        return retrieval.retrieveScoped(request, scope.classificationCeiling(), scope.sourceIds());
    }
}
