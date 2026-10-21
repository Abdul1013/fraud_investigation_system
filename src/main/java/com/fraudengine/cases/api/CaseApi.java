/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/cases/api/CaseApi.java
 * CONTEXT:     cases
 * LAYER:       api
 * PURPOSE:     Defines a typed read boundary for case details.
 * ----------------------------------------------------------------------------
 * OWNER:       Case Management
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The implementation must resolve CurrentCaller and enforce CaseReadPolicy before returning details.
 *   - No actor, role, or approval flag is accepted from an HTTP or model caller.
 *   - The old Object-based create scaffold had no implementation; writes will have separate contracts.
 *   - CaseService resolves identity and persisted grants before returning either output.
 * ============================================================================
 */
package com.fraudengine.cases.api;

import com.fraudengine.cases.api.dto.CaseDto;
import com.fraudengine.cases.api.dto.CaseEvidenceScope;

public interface CaseApi {
    CaseDto getCase(String caseId);

    CaseEvidenceScope evidenceScope(String caseId);
}
