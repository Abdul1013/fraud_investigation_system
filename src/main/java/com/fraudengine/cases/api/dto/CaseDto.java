/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/cases/api/dto/CaseDto.java
 * CONTEXT:     cases
 * LAYER:       api
 * PURPOSE:     Returns minimal case identifiers and summary without persistence entities.
 * ----------------------------------------------------------------------------
 * OWNER:       Case Management
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The response deliberately excludes account identifiers and raw sensitive evidence.
 *   - The case owner maps its domain state to this public status contract to preserve module boundaries.
 *   - This DTO is an output contract, not a trusted identity or authorization token.
 * ============================================================================
 */
package com.fraudengine.cases.api.dto;

import java.util.Objects;

public record CaseDto(String caseId, Status status, String summary) {
    public CaseDto {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(summary, "summary");
        if (caseId.isBlank() || !caseId.equals(caseId.strip())) {
            throw new IllegalArgumentException("caseId must be nonblank without surrounding whitespace");
        }
    }

    public enum Status {
        NEW, TRIAGED, INVESTIGATING, PENDING_APPROVAL, RESOLVED, CLOSED, DEGRADED
    }
}
