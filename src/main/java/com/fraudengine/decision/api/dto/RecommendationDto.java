/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/api/dto/RecommendationDto.java
 * CONTEXT:     decision
 * LAYER:       api
 * PURPOSE:     Return a persisted proposal reference without authority to execute it.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Binding digest covers the immutable server-owned action and evidence version envelope.
 *   - Proposal status never means the simulated account has been frozen.
 * ============================================================================
 */
package com.fraudengine.decision.api.dto;

import java.util.UUID;

public record RecommendationDto(UUID id, String caseId, String state, String bindingDigest) {}
