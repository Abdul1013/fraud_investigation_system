/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/CallerContext.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Carries an immutable authenticated subject and its granted capabilities.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only a trusted identity adapter should construct the context used by an application service.
 *   - This value is not authentication proof; never deserialize it from a request or tool arguments.
 *   - Case assignments and classification clearance are separate checks, not implied by capabilities.
 * ============================================================================
 */
package com.fraudengine.platform.api;

import java.util.Objects;
import java.util.Set;

public record CallerContext(String subject, Set<Permission> permissions) {
    public CallerContext {
        Objects.requireNonNull(subject, "subject");
        if (subject.isBlank() || !subject.equals(subject.strip())) {
            throw new IllegalArgumentException("subject must be nonblank without surrounding whitespace");
        }
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(Objects.requireNonNull(permission, "permission"));
    }
}
