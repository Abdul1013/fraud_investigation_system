/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/orchestration/LaunchCapacityIntegrationTest.java
 * CONTEXT:     orchestration
 * LAYER:       test
 * PURPOSE:     Prove transactional active-reservation capacity and duplicate identity against PostgreSQL.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The owning-context store is exercised with real Flyway schemas and a stub audit boundary; no model work runs.
 * ============================================================================
 */
package com.fraudengine.orchestration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.orchestration.infrastructure.*;
import com.fraudengine.platform.gateway.PromptRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class LaunchCapacityIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> DB =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Test
  void boundedLaunchesRetainDuplicateIdentity() {
    var data = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    org.flywaydb.core.Flyway.configure().dataSource(data).load().migrate();
    var jdbc = new JdbcTemplate(data);
    var audit = mock(com.fraudengine.audit.api.AuditApi.class);
    var repository =
        new InvestigationRepository(
            jdbc, audit, mock(com.fraudengine.platform.api.CurrentCaller.class));
    var telemetry = mock(com.fraudengine.platform.api.OperationalTelemetry.class);
    var store =
        new DurableInvestigationStore(
            jdbc,
            repository,
            audit,
            new PromptRegistry(new ObjectMapper(), "investigator-v1"),
            telemetry,
            mock(com.fraudengine.platform.api.ReleaseCatalog.class),
            new ObjectMapper());
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
    var first =
        transaction.execute(
            status ->
                store.launch("synthetic-case", "capacity-fixture", "Read evidence", "capacity-0"));
    for (int i = 1; i < 20; i++) {
      String key = "capacity-" + i;
      transaction.execute(
          status -> store.launch("synthetic-case", "capacity-fixture", "Read evidence", key));
    }
    assertThat(
            transaction
                .execute(
                    status ->
                        store.launch(
                            "synthetic-case", "capacity-fixture", "Read evidence", "capacity-0"))
                .id())
        .isEqualTo(first.id());
    assertThatThrownBy(
            () ->
                transaction.execute(
                    status ->
                        store.launch(
                            "synthetic-case", "capacity-fixture", "Read evidence", "capacity-20")))
        .isInstanceOf(com.fraudengine.platform.api.CapacityRejectedException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchestration.execution_reservations WHERE active",
                Integer.class))
        .isEqualTo(20);
  }
}
