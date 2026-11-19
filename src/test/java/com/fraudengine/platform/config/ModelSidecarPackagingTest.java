/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/config/ModelSidecarPackagingTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Execute a read-only model sidecar and standalone probe in one network namespace.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - packaging.model=true requires locally built reviewed images; actual inference uses synthetic content only.
 *   - The model binds loopback and has no public port. CPU is bounded to two cores for a local resource demonstration, not an AWS latency prediction.
 * ============================================================================
 */
package com.fraudengine.platform.config;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "packaging.model", matches = "true")
class ModelSidecarPackagingTest {
  @Test
  void actualInferenceStaysInsideTheSharedLoopbackNamespace() throws Exception {
    try (var model =
        new GenericContainer<>(DockerImageName.parse("fraud-ollama:week34-review"))
            .withEnv("OLLAMA_NO_CLOUD", "true")
            .withEnv("OLLAMA_HOST", "127.0.0.1:11434")
            .withEnv("HOME", "/tmp")
            .withTmpFs(Map.of("/tmp", "rw,nosuid,size=128m,mode=1777"))
            .withCreateContainerCmdModifier(
                command ->
                    command.getHostConfig().withReadonlyRootfs(true).withNanoCPUs(2_000_000_000L))
            .waitingFor(Wait.forLogMessage(".*Listening on 127.0.0.1:11434.*", 1))
            .withStartupTimeout(Duration.ofSeconds(90))) {
      model.start();
      try (var probe =
          new GenericContainer<>(DockerImageName.parse("fraud-engine:week34-review"))
              .withNetworkMode("container:" + model.getContainerId())
              .withEnv("FRAUD_ROLE", "model-probe")
              .withTmpFs(Map.of("/tmp", "rw,nosuid,size=128m,mode=1777"))
              .withCreateContainerCmdModifier(
                  command -> command.getHostConfig().withReadonlyRootfs(true))
              .withStartupCheckStrategy(
                  new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(180)))) {
        try {
          probe.start();
        } catch (RuntimeException failed) {
          Files.createDirectories(Path.of("target/week34"));
          Files.writeString(
              Path.of("target/week34/model-startup-diagnostics.log"), model.getLogs());
          throw failed;
        }
        var mapper = new ObjectMapper();
        var lines =
            probe
                .getLogs()
                .lines()
                .filter(line -> line.startsWith("{") && line.contains("\"_purpose\""))
                .toList();
        assertThat(lines).hasSize(1);
        var report = mapper.readTree(lines.getFirst());
        assertThat(report.path("digestMismatchRejected").asBoolean()).isTrue();
        assertThat(report.path("consequentialEffects").asInt()).isZero();
        Files.createDirectories(Path.of("target/week34"));
        var docker = org.testcontainers.DockerClientFactory.instance().client();
        mapper
            .writerWithDefaultPrettyPrinter()
            .writeValue(
                Path.of("target/week34/model-sidecar.json").toFile(),
                Map.of(
                    "_purpose",
                    "Executed read-only loopback native sidecar",
                    "probe",
                    report,
                    "modelImageId",
                    docker.inspectImageCmd("fraud-ollama:week34-review").exec().getId(),
                    "applicationImageId",
                    docker.inspectImageCmd("fraud-engine:week34-review").exec().getId(),
                    "cpuCores",
                    2,
                    "readOnlyRoots",
                    true,
                    "cloudDisabled",
                    true));
      }
    }
  }
}
