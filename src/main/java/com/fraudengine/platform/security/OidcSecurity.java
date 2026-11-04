/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/OidcSecurity.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Validate trusted access JWTs and map linked subjects without trusting username or role claims.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only RS256 keys from the configured issuer JWKS are accepted; expiry and audience are mandatory.
 *   - Bearer API requests remain stateless. Browser authorization code plus PKCE lives in the local learning client.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "engine.security.mode", havingValue = "oidc", matchIfMissing = true)
public class OidcSecurity {
    @Bean
    JwtDecoder accessJwtDecoder(@Value("${engine.security.issuer}") String issuer,
            @Value("${engine.security.audience:fraud-api}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> required = jwt -> {
            boolean valid = jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(java.time.Instant.now())
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && jwt.getAudience().contains(audience) && "Bearer".equals(jwt.getClaimAsString("typ"));
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), required));
        return decoder;
    }

    public static JwtAuthenticationToken linked(Jwt jwt, OidcIdentityLinks links) {
        try {
            var user = links.verified(jwt.getIssuer().toString(), jwt.getSubject());
            return new JwtAuthenticationToken(jwt, user.getAuthorities(), user.getUsername());
        } catch (com.fraudengine.platform.api.AccessDeniedException denied) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
        }
    }
}
