/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/security/StreamingIdentityTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Revalidate expiration and identity-link revocation during long-lived authenticated work.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Claims are signed-token fixtures here; real Keycloak OTP evidence remains in Week 31.
 * ============================================================================
 */
package com.fraudengine.platform.security;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fraudengine.platform.api.AccessDeniedException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
class StreamingIdentityTest {
 @Test void capturedAuthenticationExpiresAndDisabledLinkIsCheckedAgain(){
  @SuppressWarnings("unchecked") ObjectProvider<OidcIdentityLinks> provider=mock(ObjectProvider.class);
  var links=mock(OidcIdentityLinks.class);when(provider.getObject()).thenReturn(links);
  var current=new SpringSecurityCurrentCaller(provider);
  try{
   var expired=Jwt.withTokenValue("fixture-only").header("alg","RS256").issuer("https://issuer.example").subject("external").issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().minusSeconds(1)).build();
   SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(expired,List.of(),"investigator"));
   assertThatThrownBy(current::requireAuthenticated).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(links);
   var valid=Jwt.withTokenValue("fixture-only").header("alg","RS256").issuer("https://issuer.example").subject("external").issuedAt(Instant.now().minusSeconds(1)).expiresAt(Instant.now().plusSeconds(60)).build();
   SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(valid,List.of(),"investigator"));
   when(links.verified("https://issuer.example","external")).thenThrow(new AccessDeniedException());
   assertThatThrownBy(current::requireAuthenticated).isInstanceOf(AccessDeniedException.class);verify(links).verified("https://issuer.example","external");
  }finally{SecurityContextHolder.clearContext();}
 }
}
