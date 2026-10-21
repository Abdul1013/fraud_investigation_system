/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/api/DecisionApi.java
 * CONTEXT:     decision
 * LAYER:       api
 * PURPOSE:     Separate model-visible proposal creation from human approval and gated execution.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only backend evidence results may enter propose; the implementation revalidates their exact lineage.
 *   - Approve, reject, and execute are never registered as model or MCP investigator tools.
 * ============================================================================
 */
package com.fraudengine.decision.api;

import com.fraudengine.decision.api.dto.*;
import com.fraudengine.knowledge.api.dto.RetrievalResult;
import java.util.UUID;

public interface DecisionApi {
    RecommendationDto propose(String caseId, String rationale, RetrievalResult evidence, String modelVersion);
    RecommendationReview review(UUID recommendationId);
    ApprovalTokenDto approve(UUID recommendationId);
    RecommendationDto reject(UUID recommendationId);
    ExecutionResult execute(UUID recommendationId, String token, String idempotencyKey);
}
