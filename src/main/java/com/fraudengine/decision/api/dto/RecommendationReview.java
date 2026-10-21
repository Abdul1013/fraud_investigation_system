/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/api/dto/RecommendationReview.java
 * CONTEXT:     decision
 * LAYER:       api
 * PURPOSE:     Expose the exact persisted evidence and action binding for human review.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Reading rechecks the case grant and evidence scope; the response contains no approval credential.
 * ============================================================================
 */
package com.fraudengine.decision.api.dto;

import com.fraudengine.knowledge.api.dto.RetrievalResult;

public record RecommendationReview(RecommendationDto recommendation, String action, String target,
        String rationale, RetrievalResult evidence, String modelVersion, String promptVersion) {}
