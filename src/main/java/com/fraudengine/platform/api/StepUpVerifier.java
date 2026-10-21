/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/StepUpVerifier.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Requires a verified fresh second-factor assertion for the authenticated subject.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Implementations must verify issuer, audience, method, subject, and freshness with the identity provider.
 *   - A request-body boolean or analyst authority alone must never satisfy this port.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public interface StepUpVerifier {
    void requireVerified(String subject);
}
