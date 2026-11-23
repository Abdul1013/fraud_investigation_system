/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/config/ContainerPackagingIntegrationTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Run real non-root image roles against persistent local PostgreSQL and Temporal.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - packaging.run=true requires prebuilt reviewed images; no cloud resources or real identity credentials are used.
 *   - The explicit local-learning profile isolates packaging from genuine OTP/native semantic checks recorded separately.
 * ============================================================================
 */
package com.fraudengine.platform.config;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "packaging.run", matches = "true")
class ContainerPackagingIntegrationTest {
  GenericContainer<?> image(String image, Network network, Map<String, String> environment) {
    return new GenericContainer<>(DockerImageName.parse(image))
        .withNetwork(network)
        .withEnv(environment)
        .withTmpFs(Map.of("/tmp", "rw,nosuid,size=128m,mode=1777"))
        .withCreateContainerCmdModifier(
            command -> command.getHostConfig().withReadonlyRootfs(true));
  }

  GenericContainer<?> job(String image, Network network, Map<String, String> environment) {
    return image(image, network, environment)
        .withStartupCheckStrategy(
            new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(150)));
  }

  void await(BooleanSupplier check) throws Exception {
    for (int n = 0; n < 60; n++) {
      if (check.getAsBoolean()) return;
      Thread.sleep(500);
    }
    throw new AssertionError("Bounded readiness condition did not recover");
  }

  boolean workerReady(GenericContainer<?> worker) {
    try {
      return worker.execInContainer("java", "-cp", "/app", "HealthProbe").getExitCode() == 0;
    } catch (Exception unavailable) {
      return false;
    }
  }

  int readiness(GenericContainer<?> api) {
    try {
      return HttpClient.newHttpClient()
          .send(
              HttpRequest.newBuilder(
                      URI.create(
                          "http://"
                              + api.getHost()
                              + ":"
                              + api.getMappedPort(8080)
                              + "/actuator/health/readiness"))
                  .timeout(Duration.ofSeconds(4))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.discarding())
          .statusCode();
    } catch (Exception unavailable) {
      return 0;
    }
  }

  @Test
  void migrationApiWorkerAndServiceRestartAreReal() throws Exception {
    String app = "fraud-engine:week34-review";
    var docker = DockerClientFactory.instance().client();
    var inspected = docker.inspectImageCmd(app).exec();
    assertThat(inspected.getConfig().getUser()).isEqualTo("10001:10001");
    String expected = System.getProperty("packaging.expectedSourceSha", "");
    assertThat(expected).matches("[0-9a-f]{64}");
    assertThat(inspected.getConfig().getLabels().get("org.fraud.source-tree-sha256"))
        .isEqualTo(expected);
    var secrets = new HashMap<String, String>();
    for (String key :
        List.of(
            "POSTGRES_PASSWORD",
            "MIGRATOR_PASSWORD",
            "RUNTIME_PASSWORD",
            "TEMPORAL_PASSWORD",
            "KEYCLOAK_DB_PASSWORD")) secrets.put(key, UUID.randomUUID().toString());
    try (var network = Network.newNetwork();
        var database =
            new PostgreSQLContainer<>(
                    DockerImageName.parse(
                            "pgvector/pgvector:pg16@sha256:7b822b0aac60967beb1ea5e576b8602c94c300a157d187f385ae3e0da199b90a")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("postgres")
                .withUsername("bootstrap_admin")
                .withPassword(secrets.get("POSTGRES_PASSWORD"))
                .withNetwork(network)
                .withNetworkAliases("database")) {
      database.start();
      String base = "jdbc:postgresql://database:5432/";
      var bootstrapEnv = new HashMap<>(secrets);
      bootstrapEnv.putAll(
          Map.of(
              "FRAUD_ROLE",
              "bootstrap",
              "FRAUD_PROFILES",
              "local-learning",
              "POSTGRES_URL",
              base + "postgres",
              "POSTGRES_USER",
              "bootstrap_admin"));
      try (var bootstrap = job(app, network, bootstrapEnv)) {
        bootstrap.start();
        for (String secret : secrets.values())
          assertThat(!bootstrap.getLogs().contains(secret))
              .as("Bootstrap logs exclude generated private inputs")
              .isTrue();
      }
      try (var migration =
          job(
              app,
              network,
              Map.of(
                  "FRAUD_ROLE",
                  "migrate",
                  "FRAUD_PROFILES",
                  "local-learning",
                  "POSTGRES_URL",
                  base + "fraud",
                  "POSTGRES_USER",
                  "fraud_migrator",
                  "POSTGRES_PASSWORD",
                  secrets.get("MIGRATOR_PASSWORD")))) {
        migration.start();
      }
      var temporalEnvironment =
          Map.of(
              "POSTGRES_SEEDS",
              "database",
              "POSTGRES_PWD",
              secrets.get("TEMPORAL_PASSWORD"),
              "TEMPORAL_LOCAL_LEARNING",
              "true");
      try (var schema = job("fraud-temporal-schema:week34-review", network, temporalEnvironment)) {
        schema.start();
      }
      try (var temporal =
          image("fraud-temporal:week34-review", network, temporalEnvironment)
              .withNetworkAliases("temporal")
              .withExposedPorts(7233)
              .waitingFor(Wait.forListeningPort())
              .withStartupTimeout(Duration.ofSeconds(120))) {
        temporal.start();
        var namespaceEnv = new HashMap<>(temporalEnvironment);
        namespaceEnv.putAll(
            Map.of("REGISTER_NAMESPACE_ONLY", "true", "TEMPORAL_ADDRESS", "temporal:7233"));
        try (var namespace = job("fraud-temporal-schema:week34-review", network, namespaceEnv)) {
          try {
            namespace.start();
          } catch (RuntimeException failed) {
            String diagnostics = temporal.getLogs();
            for (String secret : secrets.values())
              diagnostics = diagnostics.replace(secret, "[REDACTED]");
            Files.createDirectories(Path.of("target/week34"));
            Files.writeString(
                Path.of("target/week34/temporal-startup-diagnostics.log"), diagnostics);
            throw failed;
          }
        }
        var appEnv = new HashMap<String, String>();
        appEnv.putAll(
            Map.of(
                "FRAUD_PROFILES",
                "local-learning",
                "POSTGRES_URL",
                base + "fraud",
                "POSTGRES_USER",
                "fraud_runtime",
                "POSTGRES_PASSWORD",
                secrets.get("RUNTIME_PASSWORD"),
                "SPRING_FLYWAY_ENABLED",
                "false",
                "SPRING_BATCH_JDBC_INITIALIZE_SCHEMA",
                "never",
                "AI_RELEASE_FILE",
                "",
                "INVESTIGATION_TEMPORAL_ADDRESS",
                "temporal:7233",
                "MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED",
                "true",
                "MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE",
                "readinessState,db,workflow"));
        appEnv.put("FRAUD_ROLE", "api");
        try (var api =
            image(app, network, appEnv)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/actuator/health/readiness").forStatusCode(200))
                .withStartupTimeout(Duration.ofSeconds(120))) {
          api.start();
          var workerEnv = new HashMap<>(appEnv);
          workerEnv.put("FRAUD_ROLE", "worker");
          try (var worker =
              image(app, network, workerEnv)
                  .waitingFor(Wait.forLogMessage(".*Started FraudEngineApplication.*", 1))
                  .withStartupTimeout(Duration.ofSeconds(120))) {
            worker.start();
            await(() -> workerReady(worker));
            assertThat(api.execInContainer("id", "-u").getStdout().strip()).isEqualTo("10001");
            assertThat(worker.execInContainer("id", "-u").getStdout().strip()).isEqualTo("10001");
            docker.stopContainerCmd(temporal.getContainerId()).withTimeout(10).exec();
            await(() -> readiness(api) == 503);
            await(() -> !workerReady(worker));
            docker.startContainerCmd(temporal.getContainerId()).exec();
            await(() -> readiness(api) == 200);
            await(() -> workerReady(worker));
            for (var container : List.of(api, worker, temporal))
              for (String secret : secrets.values())
                assertThat(!container.getLogs().contains(secret))
                    .as("Service logs exclude generated private inputs")
                    .isTrue();
            var result = new LinkedHashMap<String, Object>();
            result.put(
                "_purpose", "Executed local image roles and PostgreSQL-backed Temporal restart");
            result.put("applicationImageId", inspected.getId());
            result.put("sourceTreeSha256", expected);
            result.put("apiUid", 10001);
            result.put("workerUid", 10001);
            result.put("readOnlyRoots", true);
            result.put("apiReadinessSequence", List.of(200, 503, 200));
            result.put("workerReadinessSequence", List.of(true, false, true));
            result.put("nativeModelCalls", false);
            result.put(
                "profile", "explicit local-learning; genuine OTP/native model checks are separate");
            result.put("cloudDeployment", "pending approval");
            Files.createDirectories(Path.of("target/week34"));
            new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(Path.of("target/week34/container-smoke.json").toFile(), result);
          }
        }
      }
    }
  }
}
