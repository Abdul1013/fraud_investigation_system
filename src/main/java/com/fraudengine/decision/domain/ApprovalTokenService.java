/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/domain/ApprovalTokenService.java
 * CONTEXT:     decision
 * LAYER:       domain
 * PURPOSE:     Signs and verifies fixed-algorithm, audience-bound approval JWTs using Nimbus.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The user-approved maintained JOSE library owns cryptographic encoding and verification.
 *   - A Base64 key of at least 256 bits must be configured; no default or generated signing key exists.
 *   - JWT verification is only one gate; persisted state, exact binding, authority, and consumption also apply.
 * ============================================================================
 */
package com.fraudengine.decision.domain;

import com.fraudengine.platform.api.AccessDeniedException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class ApprovalTokenService {
    private final SecretKey key;
    private static final String ISSUER = "fraudengine-week28";
    private static final String AUDIENCE = "simulated-account-execution";

    public ApprovalTokenService(@Value("${decision.approval-key:}") String encodedKey) {
        if (encodedKey.isBlank()) {
            key = null;
        } else {
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length < 32) throw new IllegalArgumentException("Approval key must be at least 256 bits");
            key = new SecretKeySpec(bytes, "HmacSHA256");
        }
    }

    public SignedApproval issue(UUID recommendationId, String binding, String approver) {
        requireKey();
        Instant issued = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expires = issued.plusSeconds(120);
        UUID approvalId = UUID.randomUUID();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).audience(List.of(AUDIENCE))
                .subject(approver).id(approvalId.toString()).issuedAt(issued).expiresAt(expires)
                .claim("purpose", "action_approval").claim("recommendation", recommendationId.toString())
                .claim("binding", binding).build();
        var token = new NimbusJwtEncoder(new ImmutableSecret<>(key)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims));
        return new SignedApproval(approvalId, token.getTokenValue(), expires);
    }

    public UUID verify(String token, UUID recommendationId, String binding, String approver) {
        requireKey();
        if (token == null || token.length() > 6000) throw new AccessDeniedException();
        try {
            var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(ISSUER)));
            var jwt = decoder.decode(token);
            if (!List.of(AUDIENCE).equals(jwt.getAudience()) || !approver.equals(jwt.getSubject())
                    || !"action_approval".equals(jwt.getClaimAsString("purpose"))
                    || !recommendationId.toString().equals(jwt.getClaimAsString("recommendation"))
                    || !binding.equals(jwt.getClaimAsString("binding")) || jwt.getExpiresAt() == null || jwt.getId() == null) {
                throw new AccessDeniedException();
            }
            return UUID.fromString(jwt.getId());
        } catch (JwtException | IllegalArgumentException error) {
            throw new AccessDeniedException();
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("Approval signing is not configured");
    }

    public record SignedApproval(UUID id, String token, Instant expiresAt) {
        @Override public String toString() { return "SignedApproval[id=" + id + ", token=<redacted>]"; }
    }
}
