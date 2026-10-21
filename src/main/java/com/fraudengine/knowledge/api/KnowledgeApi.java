/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/api/KnowledgeApi.java
 * CONTEXT:     knowledge
 * LAYER:       api
 * PURPOSE:     Provides case-authorized retrieval without exposing knowledge internals.
 * ----------------------------------------------------------------------------
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Case identity is an input; caller identity and evidence scope are resolved by the implementation.
 *   - The request can narrow retrieval but cannot supply clearance or allowed sources.
 * ============================================================================
 */
package com.fraudengine.knowledge.api;

import com.fraudengine.knowledge.api.dto.RetrievalRequest;
import com.fraudengine.knowledge.api.dto.RetrievalResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface KnowledgeApi {
    void validateCaseEvidence(String caseId, @NotNull RetrievalResult evidence);

    RetrievalResult retrieveCaseEvidence(String caseId, @NotNull @Valid RetrievalRequest request);
}
