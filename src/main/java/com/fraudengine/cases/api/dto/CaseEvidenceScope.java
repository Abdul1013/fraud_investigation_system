/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/cases/api/dto/CaseEvidenceScope.java
 * CONTEXT:     cases
 * LAYER:       api
 * PURPOSE:     Transfers a server-resolved evidence allowlist and grant clearance.
 * ----------------------------------------------------------------------------
 * OWNER:       Case Management
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only CaseApi resolves this value; request and model arguments must never supply it.
 *   - An empty source set means no evidence, not unrestricted retrieval.
 *   - C3 is deliberately unrepresentable as retrievable clearance.
 * ============================================================================
 */
package com.fraudengine.cases.api.dto;

import java.util.Set;

public record CaseEvidenceScope(String caseId, String classificationCeiling, Set<String> sourceIds) {
    public CaseEvidenceScope {
        if (caseId == null || caseId.isBlank() || !Set.of("C0", "C1", "C2").contains(classificationCeiling)) {
            throw new IllegalArgumentException("Invalid case evidence scope");
        }
        sourceIds = Set.copyOf(sourceIds);
    }
}
