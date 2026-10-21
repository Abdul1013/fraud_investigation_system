/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/UnavailableStepUpVerifier.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Rejects approvals until a trusted identity-provider integration supplies verified MFA.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Basic authentication provides no second-factor evidence, so the production adapter fails closed.
 *   - Integration tests may replace this bean with explicitly simulated verified step-up.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.AccessDeniedException;
import com.fraudengine.platform.api.StepUpVerifier;
import org.springframework.stereotype.Component;

@Component
public final class UnavailableStepUpVerifier implements StepUpVerifier {
    @Override
    public void requireVerified(String subject) { throw new AccessDeniedException(); }
}
