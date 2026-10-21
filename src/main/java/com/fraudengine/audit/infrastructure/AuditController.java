/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/audit/infrastructure/AuditController.java
 * CONTEXT:     audit
 * LAYER:       infrastructure
 * PURPOSE:     Expose authorized cursor-based case audit replay without rerunning work.
 * ----------------------------------------------------------------------------
 * OWNER:       Audit, Reporting & Compliance
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Auditor or analyst capability and a current C2 case grant are required by AuditApi.
 *   - Paging is bounded; the last returned sequence is the cursor for the next request.
 * ============================================================================
 */
package com.fraudengine.audit.infrastructure;

import com.fraudengine.audit.api.AuditApi;
import com.fraudengine.audit.api.dto.AuditEventDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuditController {
    private final AuditApi audit;
    public AuditController(AuditApi audit) { this.audit = audit; }

    @GetMapping("/v1/cases/{caseId}/audit")
    public List<AuditEventDto> history(@PathVariable String caseId,
            @RequestParam(defaultValue = "0") @PositiveOrZero long afterSequence,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return audit.history(caseId, afterSequence, limit);
    }
}
