/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/domain/ActionType.java
 * CONTEXT:     decision
 * LAYER:       domain
 * PURPOSE:     Classifies actions by their read or write effect.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-26
 * RELATED:     ADR-002
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - TODO(week-26): define action classification and catalog integration.
 *   - Write actions require explicit human approval.
 * ============================================================================
 */
package com.fraudengine.decision.domain;

public enum ActionType {
    READ,
    WRITE
}