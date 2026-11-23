/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/config/MigrationMain.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Run schema migration as a dedicated role before API or worker startup.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - This entry point does not create a Spring application, authenticate users, start workers or call models.
 *   - The migration owner creates Spring Batch metadata once; application roles receive DML without schema ownership.
 * ============================================================================
 */
package com.fraudengine.platform.config;

import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class MigrationMain {
  private MigrationMain() {}

  public static void main(String[] args) {
    var environment = System.getenv();
    String url = required(environment, "POSTGRES_URL");
    if (!"local-learning".equals(environment.get("FRAUD_PROFILES"))) requireTls(url);
    var data =
        new DriverManagerDataSource(
            url,
            required(environment, "POSTGRES_USER"),
            required(environment, "POSTGRES_PASSWORD"));
    migrate(data);
    grantRuntime(data);
    System.out.println("Schema migration and runtime grants completed");
  }

  public static void migrate(DataSource data) {
    org.flywaydb.core.Flyway.configure()
        .dataSource(data)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    var jdbc = new JdbcTemplate(data);
    if (jdbc.queryForObject("SELECT to_regclass('public.batch_job_instance')::text", String.class)
        == null)
      new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
              new org.springframework.core.io.ClassPathResource(
                  "org/springframework/batch/core/schema-postgresql.sql"))
          .execute(data);
  }

  public static void grantRuntime(DataSource data) {
    var jdbc = new JdbcTemplate(data);
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='fraud_runtime')", Boolean.class)))
      throw new IllegalStateException("Bootstrap the separate runtime role before migration");
    jdbc.execute("GRANT CONNECT ON DATABASE fraud TO fraud_runtime");
    for (String schema :
        List.of(
            "public",
            "ingestion",
            "detection",
            "case_management",
            "orchestration",
            "knowledge",
            "decision",
            "audit",
            "platform")) {
      // V1 is authoritative; only grant schemas that actually exist in this repository version.
      if (!Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname=?)", Boolean.class, schema)))
        continue;
      jdbc.execute("GRANT USAGE ON SCHEMA " + schema + " TO fraud_runtime");
      jdbc.execute(
          "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA "
              + schema
              + " TO fraud_runtime");
      jdbc.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA " + schema + " TO fraud_runtime");
    }
    jdbc.execute("REVOKE UPDATE,DELETE,TRUNCATE ON audit.action_events FROM fraud_runtime");
    jdbc.execute("REVOKE UPDATE,DELETE,TRUNCATE ON platform.ai_releases FROM fraud_runtime");
    jdbc.execute(
        "REVOKE INSERT,UPDATE,DELETE,TRUNCATE ON platform.identity_links FROM fraud_runtime");
    jdbc.execute(
        "REVOKE INSERT,UPDATE,DELETE,TRUNCATE ON"
            + " case_management.case_grants,case_management.case_sources FROM fraud_runtime");
    jdbc.execute("REVOKE ALL ON public.flyway_schema_history FROM fraud_runtime");
  }

  public static void requireTls(String url) {
    if (url == null || !url.startsWith("jdbc:postgresql://"))
      throw new IllegalStateException("Deployment database URL is invalid");
    java.net.URI parsed;
    try {
      parsed = java.net.URI.create(url.substring("jdbc:".length()));
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("Deployment database URL is invalid");
    }
    var parameters =
        parsed.getRawQuery() == null
            ? List.<String>of()
            : Arrays.asList(parsed.getRawQuery().split("&"));
    if (!url.startsWith("jdbc:postgresql://")
        || parsed.getUserInfo() != null
        || parameters.stream()
            .anyMatch(p -> p.toLowerCase(java.util.Locale.ROOT).startsWith("password="))
        || parameters.stream().filter(p -> p.startsWith("sslmode=")).count() != 1
        || !parameters.contains("sslmode=verify-full")
        || parameters.stream().noneMatch(p -> p.startsWith("sslrootcert=")))
      throw new IllegalStateException(
          "Deployment database requires verify-full TLS and an explicit trusted root bundle");
  }

  public static String required(Map<String, String> environment, String name) {
    var value = environment.get(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Missing required deployment input: " + name);
    return value;
  }
}
