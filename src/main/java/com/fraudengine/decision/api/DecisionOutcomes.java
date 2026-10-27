/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/api/DecisionOutcomes.java
 * CONTEXT:     decision
 * LAYER:       api
 * PURPOSE:     Expose opaque committed human outcomes to the workflow delivery adapter.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-29
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The owning decision context reads and acknowledges its outbox; no token or evidence is exposed.
 * ============================================================================
 */
package com.fraudengine.decision.api;

import java.util.List;
import java.util.UUID;

public interface DecisionOutcomes {
    List<Outcome> pending(int limit);
    void delivered(long sequence);
    record Outcome(long sequence,UUID recommendationId,String state,String operationKey) {}
}
