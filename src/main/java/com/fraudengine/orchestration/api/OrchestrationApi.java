/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/api/OrchestrationApi.java
 * CONTEXT:     orchestration
 * LAYER:       api
 * PURPOSE:     Launch bounded investigations and read persisted status or history.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Launch returns immediately; model work is submitted to a bounded worker pool.
 *   - Effective approval state is read from the persisted recommendation without repeating model calls.
 * ============================================================================
 */
package com.fraudengine.orchestration.api;

import java.util.List;
import java.util.UUID;

public interface OrchestrationApi {
    Run launch(String caseId, String goal, String idempotencyKey);
    Run get(UUID id);
    List<Event> events(UUID id);
    record Run(UUID id, String caseId, String state, int version, UUID recommendationId) {}
    record Event(int sequence, String type, String detail) {}
}
