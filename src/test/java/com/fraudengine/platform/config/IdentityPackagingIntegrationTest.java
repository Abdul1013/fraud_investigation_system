/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/config/IdentityPackagingIntegrationTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Verify PostgreSQL-backed optimized Keycloak and its private operator job.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - packaging.identity=true requires the reviewed local Keycloak image. Credentials are generated in memory and excluded from evidence.
 *   - This checks packaging/provisioning; the genuine password-plus-OTP challenge is separately demonstrated in Week 31.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.*;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "packaging.identity", matches = "true")
class IdentityPackagingIntegrationTest {
  @Test
  void privateIdentityLaneProvisionsAnOtpEnrollmentWithoutLeakingCredentials() throws Exception {
    var passwords = new HashMap<String, String>();
    for (String role :
        List.of("fraud_migrator", "fraud_runtime", "temporal_owner", "keycloak_owner"))
      passwords.put(role, UUID.randomUUID().toString());
    String administrator = "fixture-operator-" + UUID.randomUUID(),
        adminPassword = UUID.randomUUID().toString(),
        initial = UUID.randomUUID().toString();
    try (var network = Network.newNetwork();
        var database =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("postgres")
                .withNetwork(network)
                .withNetworkAliases("database")) {
      database.start();
      DatabaseBootstrapMain.bootstrap(
          new DriverManagerDataSource(
              database.getJdbcUrl(), database.getUsername(), database.getPassword()),
          passwords);
      var env = new HashMap<String, String>();
      env.putAll(
          Map.of(
              "KC_DB_URL",
              "jdbc:postgresql://database:5432/keycloak",
              "KC_DB_USERNAME",
              "keycloak_owner",
              "KC_DB_PASSWORD",
              passwords.get("keycloak_owner"),
              "KC_HOSTNAME",
              "https://auth.staging.example.test",
              "KC_HTTP_ENABLED",
              "true",
              "KC_PROXY_HEADERS",
              "xforwarded",
              "FRAUD_PUBLIC_API",
              "https://api.staging.example.test",
              "KC_BOOTSTRAP_ADMIN_USERNAME",
              administrator,
              "KC_BOOTSTRAP_ADMIN_PASSWORD",
              adminPassword));
      try (var identity =
          new GenericContainer<>(DockerImageName.parse("fraud-keycloak:week34-review"))
              .withNetwork(network)
              .withNetworkAliases("keycloak.fraud.internal")
              .withEnv(env)
              .withTmpFs(
                  Map.of(
                      "/tmp",
                      "rw,nosuid,size=128m,mode=1777",
                      "/opt/keycloak/data",
                      "rw,nosuid,size=128m,mode=1777"))
              .withCreateContainerCmdModifier(
                  command -> command.getHostConfig().withReadonlyRootfs(true))
              .withExposedPorts(8080, 9000)
              .waitingFor(Wait.forHttp("/health/ready").forPort(9000).forStatusCode(200))
              .withStartupTimeout(Duration.ofSeconds(180))) {
        identity.start();
        var request =
            HttpRequest.newBuilder(
                    URI.create(
                        "http://"
                            + identity.getHost()
                            + ":"
                            + identity.getMappedPort(8080)
                            + "/realms/fraud/.well-known/openid-configuration"))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "auth.staging.example.test")
                .GET()
                .build();
        var response =
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var mapper = new ObjectMapper();
        var metadata = mapper.readTree(response.body());
        assertThat(metadata.path("issuer").asText())
            .isEqualTo("https://auth.staging.example.test/realms/fraud");
        var operatorEnv =
            Map.of(
                "FRAUD_ROLE",
                "identity-admin",
                "HOME",
                "/tmp",
                "IDENTITY_ADMIN_ACTION",
                "user-create",
                "PROVISION_USERNAME",
                "synthetic-" + UUID.randomUUID(),
                "PROVISION_PASSWORD",
                initial,
                "KC_BOOTSTRAP_ADMIN_USERNAME",
                administrator,
                "KC_BOOTSTRAP_ADMIN_PASSWORD",
                adminPassword);
        try (var operator =
            new GenericContainer<>(DockerImageName.parse("fraud-engine:week34-review"))
                .withNetwork(network)
                .withEnv(operatorEnv)
                .withTmpFs(Map.of("/tmp", "rw,nosuid,size=128m,mode=1777"))
                .withCreateContainerCmdModifier(
                    command -> {
                      command.getHostConfig().withReadonlyRootfs(true);
                    })
                .withStartupCheckStrategy(
                    new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(90)))) {
          operator.start();
          assertThat(operator.getLogs()).contains("Private new-identity provisioning");
          for (var container : List.of(identity, operator))
            for (String secret : List.of(adminPassword, initial, passwords.get("keycloak_owner")))
              assertThat(!container.getLogs().contains(secret))
                  .as("Identity logs exclude generated private inputs")
                  .isTrue();
          Files.createDirectories(Path.of("target/week34"));
          mapper
              .writerWithDefaultPrettyPrinter()
              .writeValue(
                  Path.of("target/week34/identity-packaging.json").toFile(),
                  Map.of(
                      "_purpose",
                      "Executed optimized PostgreSQL Keycloak and private provisioning job",
                      "issuer",
                      metadata.path("issuer").asText(),
                      "readOnlyRoots",
                      true,
                      "privateUserProvisioned",
                      true,
                      "otpEnrollmentRequired",
                      true,
                      "genuineOtpChallenge",
                      "separate Week 31 evidence",
                      "cloudDeployment",
                      "pending approval"));
        }
      }
    }
  }
}
