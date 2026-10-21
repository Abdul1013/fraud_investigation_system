/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/api/dto/ApprovalTokenDto.java
 * CONTEXT:     decision
 * LAYER:       api
 * PURPOSE:     Return a short-lived approval credential only to an authorized human caller.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Token values must never enter model messages, logs, URLs, or audit event bodies.
 *   - The database approval identifier and state enforce single use independently of the signature.
 * ============================================================================
 */
package com.fraudengine.decision.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalTokenDto(UUID recommendationId, String token, Instant expiresAt) {
    @Override
    public String toString() { return "ApprovalTokenDto[recommendationId=" + recommendationId + ", token=<redacted>]"; }
}
