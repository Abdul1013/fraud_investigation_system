/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/AuthenticatedWork.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Captures the verified caller for bounded background work.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Capture happens on the authenticated launch thread, never from a model-supplied identity.
 *   - The adapter must restore or clear thread identity after the worker finishes.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public interface AuthenticatedWork {
    Runnable capture(Runnable work);
}
