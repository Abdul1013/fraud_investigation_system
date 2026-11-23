/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/config/ReleaseManifestContractTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Parse learning manifests and reject public or automatic deployment boundaries.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - These are configuration checks, not a live Kubernetes deployment or AWS rollout.
 * ============================================================================
 */
package com.fraudengine.platform.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class ReleaseManifestContractTest {
  @SuppressWarnings("unchecked")
  Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  Yaml parser() {
    return new Yaml(new SafeConstructor(new LoaderOptions()));
  }

  @Test
  void localKubernetesExampleHasNoCloudLoadBalancerOrEmbeddedDatabasePasswords() throws Exception {
    int count = 0;
    for (String file :
        List.of(
            "namespace-and-database.yml",
            "bootstrap-job.yml",
            "migrate-job.yml",
            "application.yml"))
      for (Object value : parser().loadAll(Files.readString(Path.of("infra/k8s", file)))) {
        var resource = map(value);
        String kind = (String) resource.get("kind");
        count++;
        assertThat(kind).isIn("Namespace", "Service", "StatefulSet", "Job", "Deployment");
        if (kind.equals("Service"))
          assertThat(map(resource.get("spec")).getOrDefault("type", "ClusterIP"))
              .isEqualTo("ClusterIP");
        if (kind.equals("Deployment") || kind.equals("Job")) {
          var pod = map(map(map(resource.get("spec")).get("template")).get("spec"));
          assertThat(map(pod.get("securityContext")).get("runAsNonRoot")).isEqualTo(true);
          for (Object item : (List<?>) pod.get("containers")) {
            var container = map(item);
            assertThat(map(container.get("securityContext")).get("readOnlyRootFilesystem"))
                .isEqualTo(true);
            for (Object entry : (List<?>) container.get("env")) {
              var env = map(entry);
              if (env.get("name").toString().endsWith("PASSWORD"))
                assertThat(env).containsKey("valueFrom").doesNotContainKey("value");
            }
          }
        }
      }
    assertThat(count).isEqualTo(7);
  }

  @Test
  void releaseWorkflowRequiresManualDispatchProtectedEnvironmentAndPreflight() throws Exception {
    var workflow = map(parser().load(Files.readString(Path.of(".github/workflows/release.yml"))));
    var trigger = map(workflow.containsKey("on") ? workflow.get("on") : workflow.get(Boolean.TRUE));
    assertThat(trigger.keySet()).containsExactly("workflow_dispatch");
    var deploy = map(map(workflow.get("jobs")).get("deploy"));
    assertThat(deploy.get("environment")).isEqualTo("staging");
    assertThat(deploy.get("if")).isEqualTo("inputs.execute");
    assertThat(deploy.get("needs")).isEqualTo("preflight");
  }
}
