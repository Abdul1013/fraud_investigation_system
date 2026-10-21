/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/ConfiguredServiceIdentity.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Bind and restore a narrowly provisioned local service identity for stdio callbacks.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only existing investigator-capable accounts without approval rights may be bound.
 *   - Process launch and local OS access are the trust boundary; no identity comes from tool arguments.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.*;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredServiceIdentity implements LocalServiceIdentity {
    private final UserDetailsService users;
    public ConfiguredServiceIdentity(UserDetailsService users) { this.users = users; }

    @Override
    public <T> T call(String subject, Supplier<T> callback) {
        var user = users.loadUserByUsername(subject);
        var authorities = user.getAuthorities().stream().map(authority -> authority.getAuthority()).toList();
        if (!authorities.contains(Permission.PROPOSE_ACTION.name())
                || !authorities.contains(Permission.READ_CASE.name())
                || !authorities.contains(Permission.READ_EVIDENCE.name())
                || authorities.contains(Permission.APPROVE_ACTION.name())) {
            throw new AccessDeniedException();
        }
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        try {
            SecurityContextHolder.setContext(context);
            return callback.get();
        } finally { SecurityContextHolder.setContext(previous); }
    }
}
