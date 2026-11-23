/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/config/ReleaseMigrationIntegrationTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Verify upgrade from Week 28 and separate database ownership against PostgreSQL.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Legacy JSON/digests and pending human recommendations must survive; uncertain interrupted runs require explicit recovery.
 *   - Ephemeral database passwords are generated in memory and never written to evidence.
 * ============================================================================
 */
package com.fraudengine.platform.config;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ReleaseMigrationIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> DB =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Test
  void migrateFromWeek28WithoutChangingPendingHumanBindings() throws Exception {
    var data = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    Flyway.configure().dataSource(data).target("11").load().migrate();
    var jdbc = new JdbcTemplate(data);
    var interrupted = UUID.randomUUID();
    var pending = UUID.randomUUID();
    var recommendation = UUID.randomUUID();
    String binding = "{\"legacy\":true}", digest = "a".repeat(64);
    jdbc.update(
        "INSERT INTO orchestration.investigations(id,case_id,subject,request_key,goal,state) VALUES"
            + " (?,'migration-case','investigator','interrupted','Synthetic','INVESTIGATING'),(?,'migration-case','investigator','pending','Synthetic','PENDING_APPROVAL')",
        interrupted,
        pending);
    jdbc.update(
        "INSERT INTO"
            + " decision.recommendations(id,case_id,proposer,rationale,binding_json,binding_digest,state)"
            + " VALUES (?,'migration-case','investigator','Synthetic',?,?,'PENDING_APPROVAL')",
        recommendation,
        binding,
        digest);
    MigrationMain.migrate(data);
    MigrationMain.migrate(data);
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM orchestration.investigations WHERE id=?",
                String.class,
                interrupted))
        .isEqualTo("RECOVERY_REQUIRED");
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM orchestration.investigations WHERE id=?", String.class, pending))
        .isEqualTo("PENDING_APPROVAL");
    assertThat(
            jdbc.queryForObject(
                "SELECT binding_json FROM decision.recommendations WHERE id=?",
                String.class,
                recommendation))
        .isEqualTo(binding);
    assertThat(
            jdbc.queryForObject(
                "SELECT binding_digest FROM decision.recommendations WHERE id=?",
                String.class,
                recommendation))
        .isEqualTo(digest);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM orchestration.launch_outbox", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success", Integer.class))
        .isEqualTo(18);
    assertThat(
            jdbc.queryForObject(
                "SELECT to_regclass('public.batch_job_instance')::text", String.class))
        .isNotNull();
    java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/week34"));
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target/week34/migration-upgrade.json"),
        "{\"fromWeek\":28,\"toMigration\":18,\"pendingBindingPreserved\":true,\"interruptedRequiresRecovery\":true,\"legacyWorkflowLaunches\":0,\"repeatMigrationSafe\":true}");
  }

  @Test
  void deploymentTlsRejectsMissingVerificationOrEmbeddedCredentials() {
    assertThatCode(
            () ->
                MigrationMain.requireTls(
                    "jdbc:postgresql://private.example/fraud?sslmode=verify-full&sslrootcert=/app/aws-rds.pem"))
        .doesNotThrowAnyException();
    for (String bad :
        List.of(
            "jdbc:postgresql://private.example/fraud",
            "jdbc:postgresql://private.example/fraud?sslmode=require&sslrootcert=/app/aws-rds.pem",
            "jdbc:postgresql://user:secret@private.example/fraud?sslmode=verify-full&sslrootcert=/app/aws-rds.pem",
            "jdbc:postgresql://private.example/fraud?sslmode=disable&sslmode=verify-full&sslrootcert=/app/aws-rds.pem"))
      assertThatThrownBy(() -> MigrationMain.requireTls(bad))
          .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void applicationAndIdentityOwnersHaveSeparateDatabases() throws Exception {
    var admin = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    var passwords = new HashMap<String, String>();
    for (String role :
        List.of("fraud_migrator", "fraud_runtime", "temporal_owner", "keycloak_owner"))
      passwords.put(role, UUID.randomUUID().toString());
    DatabaseBootstrapMain.bootstrap(admin, passwords);
    var jdbc = new JdbcTemplate(admin);
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='temporal'",
                String.class))
        .isEqualTo("temporal_owner");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='keycloak'",
                String.class))
        .isEqualTo("keycloak_owner");
    assertThat(
            jdbc.queryForObject(
                "SELECT has_database_privilege('fraud_runtime','temporal','CONNECT')",
                Boolean.class))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT has_database_privilege('fraud_runtime','keycloak','CONNECT')",
                Boolean.class))
        .isFalse();
    String fraudUrl = DB.getJdbcUrl().replaceFirst("/[^/?]+(?=\\?|$)", "/fraud");
    var extensions =
        new JdbcTemplate(new DriverManagerDataSource(fraudUrl, DB.getUsername(), DB.getPassword()));
    extensions.execute("CREATE EXTENSION IF NOT EXISTS vector");
    var owner =
        new DriverManagerDataSource(fraudUrl, "fraud_migrator", passwords.get("fraud_migrator"));
    MigrationMain.migrate(owner);
    MigrationMain.grantRuntime(owner);
    var runtime =
        new JdbcTemplate(
            new DriverManagerDataSource(fraudUrl, "fraud_runtime", passwords.get("fraud_runtime")));
    assertThat(
            runtime.queryForObject("SELECT count(*) FROM knowledge.corpus_versions", Integer.class))
        .isZero();
    assertThatThrownBy(() -> runtime.execute("CREATE TABLE platform.forbidden_runtime_ddl(id int)"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(
            runtime.queryForObject(
                "SELECT has_table_privilege(current_user,'audit.action_events','UPDATE')",
                Boolean.class))
        .isFalse();
    assertThat(
            runtime.queryForObject(
                "SELECT has_table_privilege(current_user,'platform.ai_releases','UPDATE')",
                Boolean.class))
        .isFalse();
    assertThat(
            runtime.queryForObject(
                "SELECT has_table_privilege(current_user,'platform.identity_links','UPDATE')",
                Boolean.class))
        .isFalse();
    assertThat(
            runtime.queryForObject(
                "SELECT has_table_privilege(current_user,'case_management.case_grants','INSERT')",
                Boolean.class))
        .isFalse();
  }
}
