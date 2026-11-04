/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/SecurityStartupPolicy.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Fail startup when a deployment profile selects learning-only identity or untrusted source settings.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The check also runs in non-web worker processes, preventing profile combinations from bypassing API guards.
 *   - Only loopback learning permits cleartext OIDC. Staging and production require HTTPS and source trust.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import java.net.URI;
import java.util.Set;
import org.springframework.boot.*;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;

@Component
public class SecurityStartupPolicy implements ApplicationRunner {
  private final Environment environment;

  public SecurityStartupPolicy(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    validate(environment);
  }

  public static void validate(Environment environment) {
    boolean deployment = environment.acceptsProfiles(Profiles.of("staging", "prod"));
    String mode = environment.getProperty("engine.security.mode", "oidc");
    if ("basic".equals(mode)) {
      if (deployment || !environment.acceptsProfiles(Profiles.of("test", "local-learning", "mcp")))
        throw new IllegalStateException(
            "Basic identity is restricted to explicit learning/test/local stdio profiles");
    } else if ("oidc".equals(mode)) {
      URI issuer = URI.create(environment.getRequiredProperty("engine.security.issuer"));
      boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(issuer.getHost());
      if (!"https".equals(issuer.getScheme()) && (deployment || !local))
        throw new IllegalStateException(
            "Deployment OIDC requires HTTPS; HTTP is loopback learning only");
    } else
      throw new IllegalStateException("Unknown security mode");
    if (deployment) {
      com.fraudengine.platform.config.MigrationMain.requireTls(
          environment.getRequiredProperty("spring.datasource.url"));
      if (environment.getProperty("spring.flyway.enabled", Boolean.class, true)
          || !"never".equals(environment.getProperty("spring.batch.jdbc.initialize-schema"))
          || !"temporal".equals(environment.getProperty("investigation.engine"))
          || !"ollama".equals(environment.getProperty("investigation.model-provider"))
          || !"ollama".equals(environment.getProperty("knowledge.retrieval.embedding-provider")))
        throw new IllegalStateException(
            "Deployment requires dedicated migration and native durable execution roles");
      boolean worker = environment.acceptsProfiles(Profiles.of("worker"));
      boolean api = environment.acceptsProfiles(Profiles.of("api"));
      if (worker == api)
        throw new IllegalStateException("Deployment requires exactly one API or worker role");
      String signing = environment.getProperty("decision.approval-key", "");
      if (worker && !signing.isBlank())
        throw new IllegalStateException("Worker processes must not receive approval credentials");
      if (api) {
        try {
          if (java.util.Base64.getDecoder().decode(signing).length < 32)
            throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
          throw new IllegalStateException("API requires a valid private approval signing key");
        }
      }
    }
    if (deployment && environment.getProperty("release.active-file", "").isBlank())
      throw new IllegalStateException("Deployment requires an immutable AI release configuration");
    if (deployment && !environment.getProperty("knowledge.trust.required", Boolean.class, true))
      throw new IllegalStateException("Deployment requires reviewed source provenance");
  }
}
