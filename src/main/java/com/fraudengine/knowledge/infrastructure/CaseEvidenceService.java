/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/infrastructure/CaseEvidenceService.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Authorize scoped reads before cache access and revalidate results after bounded model work.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Cross-context access uses public CaseApi and platform admission contracts only.
 *   - Cache entries contain evidence, never approval authority. Human validation bypasses read-rate budgeting.
 *   - Model I/O runs outside database transactions; the separate validator opens a short delivery snapshot.
 * ============================================================================
 */
package com.fraudengine.knowledge.infrastructure;

import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.knowledge.retrieval.*;
import com.fraudengine.platform.api.ReadAdmission;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class CaseEvidenceService implements KnowledgeApi {
  private final CaseApi cases;
  private final AnswerService answers;
  private final RetrievalService retrieval;
  private final AuthorizedEvidenceCache cache;
  private final CaseEvidenceValidator validator;
  private final ReadAdmission admission;

  public CaseEvidenceService(
      CaseApi cases,
      AnswerService answers,
      RetrievalService retrieval,
      AuthorizedEvidenceCache cache,
      CaseEvidenceValidator validator,
      ReadAdmission admission) {
    this.cases = cases;
    this.answers = answers;
    this.retrieval = retrieval;
    this.cache = cache;
    this.validator = validator;
    this.admission = admission;
  }

  private RetrievalResult scoped(String caseId, RetrievalRequest request) {
    var scope = cases.evidenceScope(caseId);
    RetrievalResult found;
    try {
      found =
          cache.read(
              scope,
              request,
              retrieval.version(request, true),
              () ->
                  retrieval.retrieveScoped(
                      request, scope.classificationCeiling(), scope.sourceIds()));
    } catch (com.fraudengine.platform.gateway.ModelUnavailableException unavailable) {
      found = retrieval.degradedScoped(request, scope.classificationCeiling(), scope.sourceIds());
    }
    validator.validateRead(caseId, found);
    return found;
  }

  @Override
  public AnswerResult answerCaseEvidence(String caseId, RetrievalRequest request) {
    return admission.call(
        () -> {
          var result = answers.answerRetrieved(request, scoped(caseId, request), true);
          validator.validateRead(caseId, result.retrieval());
          return result;
        });
  }

  @Override
  public RetrievalResult retrieveCaseEvidence(String caseId, RetrievalRequest request) {
    return admission.call(() -> scoped(caseId, request));
  }

  @Override
  public RetrievalResult retrievePinnedCaseEvidence(
      String caseId,
      RetrievalRequest request,
      com.fraudengine.platform.api.ReleaseEnvelope release) {
    return admission.call(
        () -> {
          var scope = cases.evidenceScope(caseId);
          var result =
              retrieval.retrievePinned(
                  request, scope.classificationCeiling(), scope.sourceIds(), release);
          validator.validateRead(caseId, result);
          return result;
        });
  }

  @Override
  public void validateCaseEvidence(String caseId, RetrievalResult evidence) {
    validator.validateDecision(caseId, evidence);
  }
}
