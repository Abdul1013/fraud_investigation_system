/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/DeploymentEnvironmentGuard.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Reject unsafe deployment configuration before database, HTTP or worker beans are created.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - ApplicationRunner is too late for a worker factory that starts during bean creation.
 *   - Run after profile configuration has loaded, then reuse the same policy for explicit local-learning compatibility.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public final class DeploymentEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
  @Override
  public int getOrder() {
    return org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    SecurityStartupPolicy.validate(environment);
  }
}
