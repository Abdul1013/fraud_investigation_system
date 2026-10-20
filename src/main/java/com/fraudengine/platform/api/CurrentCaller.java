/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/CurrentCaller.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Resolves identity at the trusted application boundary.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Services resolve the caller through this port instead of accepting caller identity in request DTOs.
 *   - An HTTP security adapter and a future MCP adapter must authenticate before supplying a caller.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public interface CurrentCaller {
    /** Returns the authenticated caller or throws AccessDeniedException. */
    CallerContext requireAuthenticated();
}
