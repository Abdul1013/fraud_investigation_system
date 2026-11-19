/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/orchestration/ReleaseRolloutIntegrationTest.java
 * CONTEXT:     orchestration
 * LAYER:       test
 * PURPOSE:     Demonstrate persisted prompt A to B to A rollback with retained model and retrieval versions.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The default run checks real PostgreSQL version persistence. release.native=true adds actual local model and embedding calls.
 *   - Same weights under retained aliases isolate prompt rollout; this is not an evaluation of different model quality.
 * ============================================================================
 */
package com.fraudengine.orchestration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.orchestration.infrastructure.*;
import com.fraudengine.platform.api.*;
import com.fraudengine.platform.gateway.*;
import com.fraudengine.platform.gateway.provider.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ReleaseRolloutIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> DB =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @TempDir Path directory;

  @Test
  void rolloutAndRollbackPreserveLaunches() throws Exception {
    var mapper = new ObjectMapper();
    var data = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    org.flywaydb.core.Flyway.configure().dataSource(data).load().migrate();
    var jdbc = new JdbcTemplate(data);
    var prompts = new PromptRegistry(mapper, "investigator-v1");
    Path active = directory.resolve("active.json");
    var catalog =
        new ReleaseRegistry(
            active.toUri().toString(), new DefaultResourceLoader(), mapper, prompts, jdbc);
    var repository =
        new InvestigationRepository(
            jdbc, mock(com.fraudengine.audit.api.AuditApi.class), mock(CurrentCaller.class));
    var store =
        new DurableInvestigationStore(
            jdbc,
            repository,
            mock(com.fraudengine.audit.api.AuditApi.class),
            prompts,
            mock(OperationalTelemetry.class),
            catalog,
            mapper);
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
    var recorded = new ArrayList<ReleaseEnvelope>();
    var outputs = new ArrayList<Map<String, Object>>();
    UUID original = null;
    for (String phase : List.of("a", "b", "a")) {
      try (var input =
          new org.springframework.core.io.ClassPathResource("releases/release-" + phase + ".json")
              .getInputStream()) {
        Files.write(active, input.readAllBytes());
      }
      var selected = catalog.active();
      String key = "rollout-" + recorded.size();
      var run =
          transaction.execute(
              status ->
                  store.launch("release-case", "investigator", "Read synthetic case status", key));
      if (original == null) original = run.id();
      var pinned = store.release(run.id());
      assertThat(pinned).isEqualTo(selected);
      recorded.add(pinned);
      if (Boolean.getBoolean("release.native")) {
        var client = new OllamaLocalClient("http://127.0.0.1:11434", mapper);
        // Constructing with the opposite default proves that the persisted pin selects the actual
        // alias.
        var adapter =
            new OllamaToolCallingAdapter(
                client, phase.equals("a") ? "qwen2.5:week34-b" : "qwen2.5:week34-a", mapper);
        var response =
            adapter.turnPinned(
                List.of(
                    new ToolCallingPort.Message(
                        "system", prompts.byReference(pinned.promptReference()).text(), null, null),
                    new ToolCallingPort.Message(
                        "user",
                        "Report this supplied fact only: synthetic case status is INVESTIGATING.",
                        null,
                        null)),
                List.of(),
                pinned.modelVersion());
        assertThat(response.modelVersion()).isEqualTo(pinned.modelVersion());
        assertThat(response.calls()).isEmpty();
        var embedding =
            new OllamaRetrievalEmbedding(client, "embeddinggemma").pinned(pinned.embeddingSpace());
        assertThat(embedding.embed("Synthetic evidence receipt")).hasSize(768);
        outputs.add(
            Map.of(
                "phase",
                phase,
                "release",
                pinned,
                "actualModelVersion",
                response.modelVersion(),
                "actualEmbeddingSpace",
                embedding.spaceId(),
                "usage",
                response.usage(),
                "reply",
                response.content(),
                "consequentialEffects",
                0));
      }
      assertThat(store.release(original)).isEqualTo(recorded.getFirst());
      var duplicate =
          transaction.execute(
              status ->
                  store.launch(
                      "release-case", "investigator", "Read synthetic case status", "rollout-0"));
      assertThat(duplicate.id()).isEqualTo(original);
    }
    assertThat(recorded.get(0)).isEqualTo(recorded.get(2)).isNotEqualTo(recorded.get(1));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.ai_releases", Integer.class))
        .isEqualTo(2);
    UUID first = original;
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE orchestration.investigations SET release_envelope=NULL WHERE id=?",
                    first))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE platform.ai_releases SET checksum=?", "b".repeat(64)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    Files.createDirectories(Path.of("target/week34"));
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            Path.of("target/week34/release-rollout.json").toFile(),
            Map.of(
                "_purpose",
                "Actual persisted A to B to A rollout; native provider calls explicitly labelled",
                "nativeModelCalls",
                Boolean.getBoolean("release.native"),
                "retainedLaunches",
                recorded,
                "nativeOutputs",
                outputs,
                "humanReview",
                "pending review",
                "cloudDeployment",
                "pending approval"));
  }
}
