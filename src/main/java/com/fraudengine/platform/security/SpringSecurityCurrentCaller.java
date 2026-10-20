/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/SpringSecurityCurrentCaller.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Translates the verified Spring Security context into the public caller port.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only authenticated nonanonymous identities are accepted; supplied actor headers are ignored.
 *   - Only known capability authorities are exported; authentication does not imply case assignment.
 *   - Permission to approve does not prove MFA; the future write gate must check verified step-up.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.AccessDeniedException;
import com.fraudengine.platform.api.CallerContext;
import com.fraudengine.platform.api.CurrentCaller;
import com.fraudengine.platform.api.Permission;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class SpringSecurityCurrentCaller implements CurrentCaller {
    @Override
    public CallerContext requireAuthenticated() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException();
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toSet());
        var permissions = Arrays.stream(Permission.values())
                .filter(permission -> authorities.contains(permission.name())).collect(Collectors.toSet());
        return new CallerContext(authentication.getName(), permissions);
    }
}
