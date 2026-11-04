/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/UnavailableStepUpVerifier.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Reject approvals in the explicit Basic learning profile, which cannot prove a second factor.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Basic authentication provides no second-factor evidence, so the Basic adapter fails closed.
 *   - Integration tests may replace this bean with explicitly simulated verified step-up.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.AccessDeniedException;
import com.fraudengine.platform.api.StepUpVerifier;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "engine.security.mode", havingValue = "basic")
public final class UnavailableStepUpVerifier implements StepUpVerifier {
    @Override
    public void requireVerified(String subject) {
        throw new AccessDeniedException();
    }
}
