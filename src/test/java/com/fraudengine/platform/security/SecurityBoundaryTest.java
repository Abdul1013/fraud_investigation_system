/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/security/SecurityBoundaryTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Exercises authentication, route scopes, and CSRF with the real filter chain.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Test-only accounts use the production account binder and password verifier.
 *   - Probe handlers avoid databases and models while checking every legacy route matcher.
 *   - Existing PostgreSQL integration tests separately exercise real controllers with CSRF-aware HTTP.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fraudengine.platform.api.AccessDeniedException;
import com.fraudengine.platform.api.CurrentCaller;
import com.fraudengine.platform.api.Permission;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(IdentityController.class)
@Import({SecurityConfig.class, SpringSecurityCurrentCaller.class, SecurityBoundaryTest.RouteProbes.class})
class SecurityBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired CurrentCaller caller;
    @Autowired UserDetailsService users;

    @DynamicPropertySource
    static void accounts(DynamicPropertyRegistry properties) {
        AuthenticatedHttp.accounts(properties);
    }

    @Test
    void identityRequiresValidCredentialsAndIgnoresSpoofedIdentityHeaders() throws Exception {
        mvc.perform(get("/v1/security/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/security/csrf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/security/me").with(httpBasic("investigator", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/security/me").with(httpBasic("investigator", AuthenticatedHttp.PASSWORD))
                        .header("X-Actor-Id", "analyst").header("X-Roles", "APPROVE_ACTION"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("investigator"))
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("APPROVE_ACTION"))));
    }

    @Test
    void legacyPostAliasesRequireAuthenticationCuratorScopeAndCsrf() throws Exception {
        for (String path : List.of("/v1/retrieve", "/v1/knowledge/retrieval", "/v1/knowledge/answers",
                "/v1/knowledge/retrieval/index", "/v1/knowledge/ingestions",
                "/v1/knowledge/ingestions/1/restart")) {
            mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(post(path).with(httpBasic("curator", AuthenticatedHttp.PASSWORD)))
                    .andExpect(status().isForbidden());
            for (String role : List.of("investigator", "analyst", "auditor")) {
                mvc.perform(post(path).with(csrf()).with(httpBasic(role, AuthenticatedHttp.PASSWORD)))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(post(path).with(csrf()).with(httpBasic("curator", AuthenticatedHttp.PASSWORD)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void legacyMetadataAndUnknownRoutesCannotBypassScopes() throws Exception {
        for (String path : List.of("/v1/knowledge/ingestions/1", "/v1/knowledge/corpus/versions")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(httpBasic("auditor", AuthenticatedHttp.PASSWORD)))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).with(httpBasic("curator", AuthenticatedHttp.PASSWORD)))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/v1/unknown").with(httpBasic("curator", AuthenticatedHttp.PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfRequiresMatchingSessionAndAuthenticationOnEveryRequest() throws Exception {
        var result = mvc.perform(get("/v1/security/csrf")
                .with(httpBasic("curator", AuthenticatedHttp.PASSWORD))).andExpect(status().isOk()).andReturn();
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString());
        String header = body.get("headerName").asText(), token = body.get("token").asText();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(post("/v1/retrieve").session(session).header(header, token)
                        .with(httpBasic("curator", AuthenticatedHttp.PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/retrieve").header(header, token)
                        .with(httpBasic("curator", AuthenticatedHttp.PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/retrieve").session(session).header(header, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnalystRoleReceivesApproval() {
        assertThat(users.loadUserByUsername("analyst").getAuthorities())
                .extracting(authority -> authority.getAuthority()).contains(Permission.APPROVE_ACTION.name());
        for (String name : List.of("investigator", "auditor", "curator")) {
            assertThat(users.loadUserByUsername(name).getAuthorities())
                    .extracting(authority -> authority.getAuthority()).doesNotContain(Permission.APPROVE_ACTION.name());
        }

    }

    @Test
    void callerPortFailsClosedWithoutAuthenticationIncludingAnonymousAuthentication() {
        SecurityContextHolder.clearContext();
        try {
            assertThatThrownBy(caller::requireAuthenticated).isInstanceOf(AccessDeniedException.class);
            SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                    "test-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            assertThatThrownBy(caller::requireAuthenticated).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void noConfiguredAccountsMeansNoLoginAndPlaintextPasswordConfigurationIsRejected() {
        var configuration = new SecurityConfig();
        var emptyUsers = configuration.userDetailsService(new SecurityAccounts(null));
        assertThatThrownBy(() -> emptyUsers.loadUserByUsername("user"))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
        assertThatThrownBy(() -> new SecurityAccounts.Account("user", "{noop}password", SecurityAccounts.Role.AUDITOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void basicAuthenticationDoesNotProveStepUp() {
        assertThatThrownBy(() -> new UnavailableStepUpVerifier().requireVerified("analyst"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @RestController
    static class RouteProbes {
        @PostMapping({"/v1/retrieve", "/v1/knowledge/retrieval", "/v1/knowledge/answers",
                "/v1/knowledge/retrieval/index", "/v1/knowledge/ingestions",
                "/v1/knowledge/ingestions/{id}/restart"})
        String postProbe() { return "ok"; }

        @GetMapping({"/v1/knowledge/ingestions/{id}", "/v1/knowledge/corpus/versions"})
        String getProbe() { return "ok"; }
    }
}
