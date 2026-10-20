/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/IdentityController.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Exposes authenticated identity and a session-bound CSRF token for clients.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Clients fetch a CSRF token and retain its session cookie for state-changing requests.
 *   - CSRF response toString redacts its token from Spring body debug logs.
 *   - No credentials, approval tokens, MFA claims, or client-defined authorities are returned.
 *   - The caller is resolved from the security port, never from body or arbitrary request headers.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.CallerContext;
import com.fraudengine.platform.api.CurrentCaller;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/security")
public class IdentityController {
    private final CurrentCaller currentCaller;

    public IdentityController(CurrentCaller currentCaller) {
        this.currentCaller = currentCaller;
    }

    @GetMapping("/me")
    public CallerContext me() {
        return currentCaller.requireAuthenticated();
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    public record CsrfResponse(String headerName, String token) {
        @Override
        public String toString() {
            return "CsrfResponse[headerName=" + headerName + ", token=<redacted>]";
        }
    }
}
