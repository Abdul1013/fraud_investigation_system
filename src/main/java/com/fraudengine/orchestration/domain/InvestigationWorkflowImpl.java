/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/domain/InvestigationWorkflowImpl.java
 * CONTEXT:     orchestration
 * LAYER:       domain
 * PURPOSE:     Coordinate retryable turns and a durable human wait without executing a write.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Temporal timers and activity results are replay-safe; no system clock, random UUID, or I/O is used here.
 *   - Human commands stay in DecisionApi. Signals report their persisted outcomes and cannot authorize execution.
 *   - Cancellation compensates only active investigation state, never an already approved simulator effect.
 * ============================================================================
 */
package com.fraudengine.orchestration.domain;

import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.CanceledFailure;
import java.time.Duration;
import java.util.Set;

public class InvestigationWorkflowImpl implements InvestigationWorkflow {
    private String state = "NEW";
    private String humanOutcome = "";
    private final InvestigationActivities activities = Workflow.newActivityStub(InvestigationActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(60))
                    .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumInterval(Duration.ofSeconds(5)).setMaximumAttempts(3)
                            .setDoNotRetry("ACCESS_DENIED", "INVALID_STEP").build()).build());

    @Override public void run(String runId) {
        try {
            state = "INVESTIGATING";
            for (int step = 0; step < 6; step++) {
                state = activities.advance(runId, step);
                if (!"CONTINUE".equals(state)) break;
            }
            if ("CONTINUE".equals(state)) { state = "FAILED"; activities.finish(runId, state); }
            if ("PENDING_APPROVAL".equals(state)) {
                boolean completed = Workflow.await(Duration.ofDays(7),
                        () -> Set.of("REJECTED", "EXECUTED").contains(humanOutcome));
                state = completed ? humanOutcome : "HUMAN_WAIT_EXPIRED";
            }
        } catch (RuntimeException failure) {
            // Activity cancellation is commonly wrapped in ActivityFailure, not a direct CanceledFailure.
            String terminal=io.temporal.workflow.CancellationScope.current().isCancelRequested()
                    || failure instanceof CanceledFailure ? "CANCELLED" : "FAILED";
            Workflow.newDetachedCancellationScope(() -> activities.finish(runId, terminal)).run();
            state=terminal;
        }
    }

    @Override public void decision(String outcome) {
        if (Set.of("APPROVED", "REJECTED", "EXECUTED").contains(outcome)) humanOutcome = outcome;
    }
    @Override public String status() { return state; }
}
