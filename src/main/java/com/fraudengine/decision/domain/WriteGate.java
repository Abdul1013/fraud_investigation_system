/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/domain/WriteGate.java
 * CONTEXT:     decision
 * LAYER:       domain
 * PURPOSE:     Rejects execution unless the persisted approval is current and unconsumed.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The decision service holds a row lock and invokes this gate inside the effect/audit transaction.
 *   - Both the stored and signed approval identifiers must match; a valid signature alone is insufficient.
 * ============================================================================
 */
package com.fraudengine.decision.domain;

import com.fraudengine.platform.api.AccessDeniedException;
import java.time.Instant;
import java.util.UUID;

public final class WriteGate {
    private final ActionCatalog catalog = new ActionCatalog();

    public void requireApproved(String action, String state, UUID storedApproval, UUID verifiedApproval, Instant expiresAt) {
        if (catalog.classify(action) != ActionType.WRITE || !"APPROVED".equals(state) || storedApproval == null || !storedApproval.equals(verifiedApproval)
                || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new AccessDeniedException();
        }
    }
}
