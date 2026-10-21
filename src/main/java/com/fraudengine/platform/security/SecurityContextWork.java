/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/SecurityContextWork.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Propagates the verified request context to a worker with automatic cleanup.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Spring Security owns context installation and restoration around the captured runnable.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.AuthenticatedWork;
import com.fraudengine.platform.api.CurrentCaller;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextWork implements AuthenticatedWork {
    private final CurrentCaller caller;
    public SecurityContextWork(CurrentCaller caller) { this.caller = caller; }
    @Override
    public Runnable capture(Runnable work) {
        caller.requireAuthenticated();
        return new DelegatingSecurityContextRunnable(work);
    }
}
