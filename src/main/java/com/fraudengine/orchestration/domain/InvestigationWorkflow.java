/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/domain/InvestigationWorkflow.java
 * CONTEXT:     orchestration
 * LAYER:       domain
 * PURPOSE:     Declare the deterministic durable workflow and opaque decision notification contract.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Workflow arguments and history contain identifiers/outcomes only, not evidence or approval credentials.
 *   - Activity implementations own every model, database, tool, and authorization operation.
 * ============================================================================
 */
package com.fraudengine.orchestration.domain;

import io.temporal.workflow.*;

@WorkflowInterface
public interface InvestigationWorkflow {
    @WorkflowMethod void run(String runId);
    @SignalMethod void decision(String state);
    @QueryMethod String status();
}
