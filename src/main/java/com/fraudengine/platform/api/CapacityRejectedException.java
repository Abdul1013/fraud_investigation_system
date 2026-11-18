/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/CapacityRejectedException.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Return an explicit bounded retry interval when read or launch capacity is exhausted.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - This exception never carries a query, credential, actor name or case identifier.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public final class CapacityRejectedException extends RuntimeException {
    private final int retryAfterSeconds;

    public CapacityRejectedException(int retryAfterSeconds) {
        super("Request capacity exceeded");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
