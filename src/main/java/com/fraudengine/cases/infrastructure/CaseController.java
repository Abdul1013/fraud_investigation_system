/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/cases/infrastructure/CaseController.java
 * CONTEXT:     cases
 * LAYER:       infrastructure
 * PURPOSE:     Exposes authenticated case reads without accepting identity claims.
 * ----------------------------------------------------------------------------
 * OWNER:       Case Management
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The route filter checks capability and CaseApi enforces the server-owned case grant.
 *   - The service returns the same denial for nonexistent, unrelated, and insufficient-clearance cases.
 * ============================================================================
 */
package com.fraudengine.cases.infrastructure;

import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.cases.api.dto.CaseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cases")
public class CaseController {
    private final CaseApi cases;

    public CaseController(CaseApi cases) {
        this.cases = cases;
    }

    @GetMapping("/{caseId}")
    public CaseDto getCase(@PathVariable String caseId) {
        return cases.getCase(caseId);
    }
}
