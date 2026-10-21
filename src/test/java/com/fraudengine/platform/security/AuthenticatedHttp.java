/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/security/AuthenticatedHttp.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Provides authenticated CSRF-aware requests for existing database integration tests.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Credentials and accounts exist only in tests; the production account configuration has no defaults.
 *   - POSTs obtain a real session-bound token through HTTP rather than bypassing security filters.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class AuthenticatedHttp {
    public static final String PASSWORD = "test-only-password";
    private static final String HASH = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD);
    private final TestRestTemplate http;

    public AuthenticatedHttp(TestRestTemplate http) {
        this(http, "curator");
    }

    public AuthenticatedHttp(TestRestTemplate http, String username) {
        this.http = http.withBasicAuth(username, PASSWORD);
    }

    public static void accounts(DynamicPropertyRegistry properties) {
        String[] names = {"curator", "investigator", "analyst", "auditor"};
        String[] roles = {"KNOWLEDGE_CURATOR", "INVESTIGATOR", "ANALYST", "AUDITOR"};
        for (int index = 0; index < names.length; index++) {
            int account = index;
            properties.add("engine.security.accounts[" + index + "].username", () -> names[account]);
            properties.add("engine.security.accounts[" + index + "].password-hash", () -> HASH);
            properties.add("engine.security.accounts[" + index + "].role", () -> roles[account]);
        }
    }

    public <T> ResponseEntity<T> postForEntity(String url, Object body, Class<T> responseType) {
        var csrf = http.getForEntity("/v1/security/csrf", Map.class);
        if (!csrf.getStatusCode().is2xxSuccessful() || csrf.getBody() == null) {
            throw new AssertionError("Could not obtain an authenticated CSRF token");
        }
        var headers = new HttpHeaders();
        headers.set((String) csrf.getBody().get("headerName"), (String) csrf.getBody().get("token"));
        var cookies = csrf.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            headers.set(HttpHeaders.COOKIE, cookies.stream().map(cookie -> cookie.split(";", 2)[0])
                    .collect(java.util.stream.Collectors.joining("; ")));
        }
        return http.postForEntity(url, new HttpEntity<>(body, headers), responseType);
    }

    public <T> T getForObject(String url, Class<T> type) {
        return http.getForObject(url, type);
    }

    public <T> ResponseEntity<T> getForEntity(String url, Class<T> type) {
        return http.getForEntity(url, type);
    }
}
