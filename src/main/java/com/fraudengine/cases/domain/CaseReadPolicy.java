/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/cases/domain/CaseReadPolicy.java
 * CONTEXT:     cases
 * LAYER:       domain
 * PURPOSE:     Requires both read capability and a server-owned case assignment.
 * ----------------------------------------------------------------------------
 * OWNER:       Case Management
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The owning case service must load assignments from its repository, never request arguments.
 *   - Capability alone cannot grant access to every case; assignment alone cannot grant a capability.
 *   - This policy does not authorize evidence disclosure, action approval, or action execution.
 * ============================================================================
 */
package com.fraudengine.cases.domain;

import com.fraudengine.platform.api.AccessDeniedException;
import com.fraudengine.platform.api.CallerContext;
import com.fraudengine.platform.api.Permission;
import java.util.Set;

public final class CaseReadPolicy {
    public void requireRead(CallerContext caller, String caseId, Set<String> assignedCaseIds) {
        if (caller == null || caseId == null || caseId.isBlank() || assignedCaseIds == null
                || !caller.hasPermission(Permission.READ_CASE) || !assignedCaseIds.contains(caseId)) {
            throw new AccessDeniedException();
        }
    }
}
