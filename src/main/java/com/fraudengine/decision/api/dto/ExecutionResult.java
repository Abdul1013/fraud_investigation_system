/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/api/dto/ExecutionResult.java
 * CONTEXT:     decision
 * LAYER:       api
 * PURPOSE:     Describe one idempotent local simulator result.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - This is a local database simulator result, never a claim about an external bank account.
 * ============================================================================
 */
package com.fraudengine.decision.api.dto;

import java.util.UUID;

public record ExecutionResult(UUID recommendationId, String caseId, boolean frozen, boolean simulated) {}
