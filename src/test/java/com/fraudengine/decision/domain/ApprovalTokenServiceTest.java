/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/decision/domain/ApprovalTokenServiceTest.java
 * CONTEXT:     decision
 * LAYER:       test
 * PURPOSE:     Check fixed-algorithm approval JWT bindings and genuine timestamp expiry.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Test credentials are isolated; the production runtime has no default signing key.
 * ============================================================================
 */
package com.fraudengine.decision.domain;

import static org.assertj.core.api.Assertions.*;
import com.fraudengine.platform.api.AccessDeniedException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class ApprovalTokenServiceTest {
    private final String key = Base64.getEncoder().encodeToString("test-only-32-byte-signing-key-for-jwt".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Test
    void verifiesExactRecommendationBindingAndApproverOnly() {
        var tokens = new ApprovalTokenService(key);
        UUID id = UUID.randomUUID();
        var issued = tokens.issue(id, "binding", "analyst");
        assertThat(tokens.verify(issued.token(), id, "binding", "analyst")).isEqualTo(issued.id());
        assertThatThrownBy(() -> tokens.verify(issued.token(), UUID.randomUUID(), "binding", "analyst"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tokens.verify(issued.token(), id, "changed-parameters", "analyst"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tokens.verify(issued.token(), id, "binding", "another-analyst"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(issued.toString()).doesNotContain(issued.token());
    }

    @Test
    void rejectsExpiredWrongAudienceAndWrongPurposeEvenWithValidSignatures() {
        var tokens = new ApprovalTokenService(key);
        UUID id = UUID.randomUUID();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256")));
        for (int scenario = 0; scenario < 5; scenario++) {
            int variant = scenario;
            var claims = JwtClaimsSet.builder().issuer("fraudengine-week28").subject("analyst")
                    .audience(List.of(scenario == 1 ? "wrong-audience" : "simulated-account-execution"))
                    .id(UUID.randomUUID().toString()).issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(scenario == 0 ? Instant.now().minusSeconds(1) : Instant.now().plusSeconds(60))
                    .claim("purpose", scenario == 2 ? "access_token" : "action_approval")
                     .claim("recommendation", id.toString()).claim("binding", "binding")
                    .claims(values -> { if (variant == 3) values.remove("aud"); if (variant == 4) values.remove("jti"); }).build();
            var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            assertThatThrownBy(() -> tokens.verify(token, id, "binding", "analyst")).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void refusesMissingOrUndersizedSigningKeys() {
        assertThatThrownBy(() -> new ApprovalTokenService("").issue(UUID.randomUUID(), "binding", "analyst"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ApprovalTokenService(Base64.getEncoder().encodeToString(new byte[8])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
