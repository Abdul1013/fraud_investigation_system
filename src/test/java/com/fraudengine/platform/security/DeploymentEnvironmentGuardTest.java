/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/security/DeploymentEnvironmentGuardTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Prove deployment policy fails before infrastructure initialization.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The test supplies synthetic configuration only; no issuer, database or model is contacted.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DeploymentEnvironmentGuardTest {
  MockEnvironment deployment() {
    var env = new MockEnvironment();
    env.setActiveProfiles("staging", "api");
    return env.withProperty("engine.security.mode", "oidc")
        .withProperty("engine.security.issuer", "https://identity.example/realms/fraud")
        .withProperty(
            "spring.datasource.url",
            "jdbc:postgresql://private.example/fraud?sslmode=verify-full&sslrootcert=/app/aws-rds.pem")
        .withProperty("spring.flyway.enabled", "false")
        .withProperty("spring.batch.jdbc.initialize-schema", "never")
        .withProperty("investigation.engine", "temporal")
        .withProperty("investigation.model-provider", "ollama")
        .withProperty("knowledge.retrieval.embedding-provider", "ollama")
        .withProperty(
            "decision.approval-key",
            java.util.Base64.getEncoder()
                .encodeToString(
                    "synthetic-public-policy-fixture-key-material"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .withProperty("release.active-file", "classpath:releases/release-a.json");
  }

  @Test
  void workersNeverReceiveApprovalSigningMaterial() {
    var worker = deployment().withProperty("decision.approval-key", "");
    worker.setActiveProfiles("staging", "worker");
    assertThatCode(() -> new DeploymentEnvironmentGuard().postProcessEnvironment(worker, null))
        .doesNotThrowAnyException();
    worker.withProperty("decision.approval-key", "synthetic-present-material");
    assertThatThrownBy(() -> new DeploymentEnvironmentGuard().postProcessEnvironment(worker, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void hardenedDeploymentPassesAndUnsafeVariantsFailEarly() {
    var guard = new DeploymentEnvironmentGuard();
    assertThatCode(() -> guard.postProcessEnvironment(deployment(), null))
        .doesNotThrowAnyException();
    for (var entry :
        java.util.Map.of(
                "engine.security.mode",
                "basic",
                "spring.flyway.enabled",
                "true",
                "knowledge.retrieval.embedding-provider",
                "fixture",
                "investigation.engine",
                "local",
                "engine.security.issuer",
                "http://identity.example/realms/fraud")
            .entrySet()) {
      var env = deployment().withProperty(entry.getKey(), entry.getValue());
      assertThatThrownBy(() -> guard.postProcessEnvironment(env, null))
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
