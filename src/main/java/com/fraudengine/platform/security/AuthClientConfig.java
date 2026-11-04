/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/AuthClientConfig.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Publish only the non-secret local public-client identity endpoints.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Public PKCE clients have no client secret. Tokens are never returned from this configuration endpoint.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@RestController
public class AuthClientConfig {
    private final String issuer;

    public AuthClientConfig(@Value("${engine.security.issuer:http://localhost:8081/realms/fraud}") String issuer) {
        this.issuer = issuer;
    }

    @GetMapping("/auth/config")
    public Map<String, String> config() {
        return Map.of("issuer", issuer, "clientId", "fraud-learning");
    }
}
