/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/domain/InvestigationActivities.java
 * CONTEXT:     orchestration
 * LAYER:       domain
 * PURPOSE:     Declare idempotent external activity boundaries for each bounded investigation turn.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only a small outcome string enters Temporal history; sensitive receipts stay in application storage.
 * ============================================================================
 */
package com.fraudengine.orchestration.domain;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface InvestigationActivities {
    String advance(String runId, int step);
    void finish(String runId, String outcome);
}
