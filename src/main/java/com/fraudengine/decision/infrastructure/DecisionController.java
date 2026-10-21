/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/infrastructure/DecisionController.java
 * CONTEXT:     decision
 * LAYER:       infrastructure
 * PURPOSE:     Expose human review, approval, rejection, and simulator execution separately from tools.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Approval accepts no MFA flag: verified step-up is obtained through the trusted platform port.
 *   - Execution credentials are request-body secrets and their string representation is redacted.
 * ============================================================================
 */
package com.fraudengine.decision.infrastructure;

import com.fraudengine.decision.api.DecisionApi;
import com.fraudengine.decision.api.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/recommendations")
public class DecisionController {
    private final DecisionApi decisions;
    public DecisionController(DecisionApi decisions) { this.decisions = decisions; }

    @GetMapping("/{id}")
    public RecommendationReview review(@PathVariable UUID id) { return decisions.review(id); }

    @PostMapping("/{id}/approve")
    public ApprovalTokenDto approve(@PathVariable UUID id) { return decisions.approve(id); }

    @PostMapping("/{id}/reject")
    public RecommendationDto reject(@PathVariable UUID id) { return decisions.reject(id); }

    @PostMapping("/{id}/execute")
    public ExecutionResult execute(@PathVariable UUID id, @Valid @RequestBody ExecuteRequest request) {
        return decisions.execute(id, request.token(), request.idempotencyKey());
    }

    public record ExecuteRequest(@NotBlank @Size(max = 6000) String token,
            @NotBlank @Size(max = 100) String idempotencyKey) {
        @Override public String toString() { return "ExecuteRequest[token=<redacted>]"; }
    }
}
