/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/config/DatabaseBootstrapMain.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Provision separate database owners using secret-injected administrator credentials.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Run once as a private administrator task before migrations. Passwords are never arguments or output.
 *   - Existing roles retain their credentials; rotation is a separate reviewed operation. Model and application processes never get master credentials.
 * ============================================================================
 */
package com.fraudengine.platform.config;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class DatabaseBootstrapMain {
  private DatabaseBootstrapMain() {}

  public static void main(String[] args) {
    var env = System.getenv();
    String url = MigrationMain.required(env, "POSTGRES_URL");
    if (!"local-learning".equals(env.get("FRAUD_PROFILES"))) MigrationMain.requireTls(url);
    var admin =
        new DriverManagerDataSource(
            url,
            MigrationMain.required(env, "POSTGRES_USER"),
            MigrationMain.required(env, "POSTGRES_PASSWORD"));
    bootstrap(
        admin,
        Map.of(
            "fraud_migrator",
            MigrationMain.required(env, "MIGRATOR_PASSWORD"),
            "fraud_runtime",
            MigrationMain.required(env, "RUNTIME_PASSWORD"),
            "temporal_owner",
            MigrationMain.required(env, "TEMPORAL_PASSWORD"),
            "keycloak_owner",
            MigrationMain.required(env, "KEYCLOAK_DB_PASSWORD")));
    String fraud = url.replaceFirst("/[^/?]+(?=\\?|$)", "/fraud");
    var extensions =
        new JdbcTemplate(
            new DriverManagerDataSource(
                fraud,
                MigrationMain.required(env, "POSTGRES_USER"),
                MigrationMain.required(env, "POSTGRES_PASSWORD")));
    extensions.execute("CREATE EXTENSION IF NOT EXISTS vector");
    extensions.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
    System.out.println("Separate database owners provisioned; run dedicated schema jobs next");
  }

  public static void bootstrap(javax.sql.DataSource admin, Map<String, String> passwords) {
    var jdbc = new JdbcTemplate(admin);
    for (String role :
        List.of("fraud_migrator", "fraud_runtime", "temporal_owner", "keycloak_owner")) {
      String password = passwords.get(role);
      if (password == null || password.length() < 24)
        throw new IllegalArgumentException("Database passwords require at least 24 characters");
      if (!Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=?)", Boolean.class, role))) {
        // PostgreSQL format quotes both identifier and literal; no caller-selected identifier is
        // accepted.
        String statement =
            jdbc.queryForObject(
                "SELECT format('CREATE ROLE %I LOGIN PASSWORD %L',?,?)",
                String.class, role, password);
        try (var connection = admin.getConnection();
            var ddl = connection.createStatement()) {
          ddl.execute(statement);
        } catch (java.sql.SQLException failure) {
          throw new IllegalStateException(
              "Role provisioning failed; inspect sanitized database administration diagnostics");
        }
      }
    }
    for (var entry :
        Map.of(
                "fraud",
                "fraud_migrator",
                "temporal",
                "temporal_owner",
                "temporal_visibility",
                "temporal_owner",
                "keycloak",
                "keycloak_owner")
            .entrySet()) {
      if (!Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM pg_database WHERE datname=?)",
              Boolean.class,
              entry.getKey())))
        jdbc.execute("CREATE DATABASE " + entry.getKey() + " OWNER " + entry.getValue());
      jdbc.execute("REVOKE CONNECT,TEMPORARY ON DATABASE " + entry.getKey() + " FROM PUBLIC");
      jdbc.execute("GRANT CONNECT ON DATABASE " + entry.getKey() + " TO " + entry.getValue());
    }
  }
}
