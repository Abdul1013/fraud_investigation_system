/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/security/StepUpClaimsTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Reject stale or insufficient verified-claim fixtures independently of the real OTP demonstration.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - These are claim-boundary unit fixtures, not proof of signature verification or an actual OTP challenge.
 *   - The Keycloak integration test supplies the separate genuine authentication evidence.
 * ============================================================================
 */
package com.fraudengine.platform.security;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fraudengine.platform.api.AccessDeniedException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.userdetails.User;

class StepUpClaimsTest {
    @Test void signedLevelAndFreshnessAreMandatoryEvenForAnApprover() {
        var links=mock(OidcIdentityLinks.class);
        when(links.verified("http://issuer","external")).thenReturn(User.withUsername("analyst").password("unused").authorities("APPROVE_ACTION").build());
        var verifier=new OidcStepUpVerifier("http://issuer","fraud-api",links);
        Map<String,Object> valid=Map.of("iss","http://issuer","sub","external","aud",List.of("fraud-api"),"acr","2","auth_time",Instant.now().getEpochSecond(),"exp",Instant.now().plusSeconds(120));
        var previous=SecurityContextHolder.getContext();
        try {
            List<Map<String,Object>> rejected=List.of(Map.of("acr","1"),Map.of("auth_time",Instant.now().minusSeconds(301).getEpochSecond()),
                Map.of("auth_time",Instant.now().plusSeconds(60).getEpochSecond()),Map.of("iss","http://other"),Map.of("aud",List.of("other")),Map.of("exp",Instant.now().minusSeconds(1)));
            for(var change:rejected) {
                Map<String,Object> claims=new HashMap<>(valid);claims.putAll(change);
                var jwt=Jwt.withTokenValue("unit-fixture").header("alg","RS256").claims(values->values.putAll(claims)).build();
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new JwtAuthenticationToken(jwt,List.of(),"analyst"));SecurityContextHolder.setContext(context);
                assertThatThrownBy(()->verifier.requireVerified("analyst")).isInstanceOf(AccessDeniedException.class);
            }
            SecurityContextHolder.clearContext();
            assertThatThrownBy(()->verifier.requireVerified("analyst")).isInstanceOf(AccessDeniedException.class);
        } finally {SecurityContextHolder.setContext(previous);}
    }
    @Test void stagingCannotEnableBasicThroughALearningProfile() {
        var environment=new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles("test","staging");
        assertThatThrownBy(()->new SecurityConfig().securityFilterChain(null,"basic",environment,null,null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("explicit local-learning");
    }

}
