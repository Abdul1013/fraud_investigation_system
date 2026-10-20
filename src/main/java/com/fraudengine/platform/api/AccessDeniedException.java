/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/AccessDeniedException.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Represents a denied access without disclosing protected resource existence.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The same public message is used for missing identity, permission, or case grant.
 *   - HTTP status mapping belongs to the future transport adapter, not this public contract.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public final class AccessDeniedException extends RuntimeException {
    public AccessDeniedException() {
        super("Access denied");
    }
}
