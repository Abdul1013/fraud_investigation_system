/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/OrchestrationController.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Launch asynchronous investigations and expose persisted status and history.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The controller performs no model work; it returns a run identifier with HTTP 202.
 *   - Repeated matching keys return the existing run without launching another worker.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;

import com.fraudengine.orchestration.api.OrchestrationApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/investigations")
public class OrchestrationController {
    private final OrchestrationApi investigations;
    public OrchestrationController(OrchestrationApi investigations) { this.investigations = investigations; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrchestrationApi.Run launch(@Valid @RequestBody LaunchRequest request) {
        return investigations.launch(request.caseId(), request.goal(), request.idempotencyKey());
    }

    @GetMapping("/{id}")
    public OrchestrationApi.Run get(@PathVariable UUID id) { return investigations.get(id); }

    @GetMapping("/{id}/events")
    public List<OrchestrationApi.Event> events(@PathVariable UUID id) { return investigations.events(id); }

    public record LaunchRequest(@NotBlank @Size(max = 100) String caseId,
            @NotBlank @Size(max = 2000) String goal, @NotBlank @Size(max = 100) String idempotencyKey) {}
}
