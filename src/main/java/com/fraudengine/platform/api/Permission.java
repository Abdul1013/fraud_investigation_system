/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/Permission.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Names capabilities independently of HTTP routes or model tool names.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Roles are mapped to capabilities by trusted authentication, not model arguments.
 *   - Approval is separate from proposal; neither permission proves step-up authentication.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public enum Permission {
    READ_CASE,
    READ_EVIDENCE,
    PROPOSE_ACTION,
    APPROVE_ACTION,
    MANAGE_KNOWLEDGE,
    READ_GLOBAL_EVIDENCE,
    READ_AUDIT
}
