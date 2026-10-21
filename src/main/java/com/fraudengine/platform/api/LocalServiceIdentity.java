/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/LocalServiceIdentity.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Run a local transport callback as an operator-provisioned service subject.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The subject must come from trusted process configuration, never MCP or model arguments.
 *   - This is local process provisioning, not remote OAuth or password authentication.
 * ============================================================================
 */
package com.fraudengine.platform.api;

import java.util.function.Supplier;

public interface LocalServiceIdentity {
    <T> T call(String configuredSubject, Supplier<T> callback);
}
