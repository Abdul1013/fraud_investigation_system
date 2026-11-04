/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/OidcStepUpVerifier.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Require a fresh signed password-plus-OTP authentication level for the linked approver.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The trusted Keycloak flow maps ACR 2 exclusively to password and OTP; body flags never satisfy this check.
 *   - Bearer tokens are kept in the security context, never in Temporal history, logs or audit event bodies.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.*;
import java.time.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

@Component
@ConditionalOnProperty(name = "engine.security.mode", havingValue = "oidc", matchIfMissing = true)
public final class OidcStepUpVerifier implements StepUpVerifier {
    private final String issuer, audience;
    private final OidcIdentityLinks links;

    public OidcStepUpVerifier(@Value("${engine.security.issuer}") String issuer,
            @Value("${engine.security.audience:fraud-api}") String audience, OidcIdentityLinks links) {
        this.issuer = issuer;
        this.audience = audience;
        this.links = links;
    }

    @Override
    public void requireVerified(String subject) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()
                || !subject.equals(token.getName()))
            throw new AccessDeniedException();
        var jwt = token.getToken();
        Instant now = Instant.now();
        Object raw = jwt.getClaims().get("auth_time");
        Instant authenticated = raw instanceof Number number ? Instant.ofEpochSecond(number.longValue()) : null;
        if (!issuer.equals(jwt.getIssuer().toString()) || !jwt.getAudience().contains(audience)
                || !"2".equals(jwt.getClaimAsString("acr")) || authenticated == null
                || authenticated.isBefore(now.minusSeconds(300)) || authenticated.isAfter(now.plusSeconds(30))
                || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(now)
                || !subject.equals(links.verified(issuer, jwt.getSubject()).getUsername()))
            throw new AccessDeniedException();
    }
}
