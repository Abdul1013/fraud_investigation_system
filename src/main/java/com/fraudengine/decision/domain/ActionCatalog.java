/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/domain/ActionCatalog.java
 * CONTEXT:     decision
 * LAYER:       domain
 * PURPOSE:     Classify the single supported consequential simulator action deterministically.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Unknown action names are rejected; no model confidence or free-form action can create an executor.
 *   - The model-visible registry separately marks reads and proposal persistence effects.
 * ============================================================================
 */
package com.fraudengine.decision.domain;

public final class ActionCatalog {
    public ActionType classify(String action) {
        if (!"SIMULATED_ACCOUNT_FREEZE".equals(action)) {
            throw new com.fraudengine.platform.api.AccessDeniedException();
        }
        return ActionType.WRITE;
    }
}
