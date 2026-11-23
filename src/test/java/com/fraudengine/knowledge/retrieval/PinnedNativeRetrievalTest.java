/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/knowledge/retrieval/PinnedNativeRetrievalTest.java
 * CONTEXT:     knowledge
 * LAYER:       test
 * PURPOSE:     Verify a retained native release retrieves its pinned corpus after a newer corpus is published.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - release.native=true is explicit. This test uses actual local embeddings and synthetic trusted policy text.
 *   - Revoking the pinned source still denies access; version pinning never preserves revoked authorization.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.knowledge.api.*;
import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.platform.api.ReleaseEnvelope;
import com.fraudengine.platform.security.AuthenticatedHttp;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
      "knowledge.trust.required=true",
      "knowledge.retrieval.embedding-provider=ollama",
      "knowledge.retrieval.embedding-model=embeddinggemma:week34-v1"
    })
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "release.native", matches = "true")
class PinnedNativeRetrievalTest {
  @Container
  static final PostgreSQLContainer<?> DB =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", DB::getJdbcUrl);
    p.add("spring.datasource.username", DB::getUsername);
    p.add("spring.datasource.password", DB::getPassword);
    AuthenticatedHttp.accounts(p);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired KnowledgeApi knowledge;
  @Autowired RetrievalIndexService index;
  @Autowired UserDetailsService users;
  @Autowired ObjectMapper mapper;

  @Test
  void newerDefaultCannotChangePinnedCorpusAndRevocationStillWins() throws Exception {
    jdbc.update(
        "INSERT INTO case_management.case_records(case_id,status,summary,classification) VALUES"
            + " ('release-pin-case','INVESTIGATING','Synthetic','C0')");
    jdbc.update(
        "INSERT INTO case_management.case_grants VALUES ('release-pin-case','investigator','C2')");
    jdbc.update(
        "INSERT INTO case_management.case_sources VALUES ('release-pin-case','release-policy')");
    for (int version = 1; version <= 2; version++) {
      String text =
          version == 1
              ? "Transaction receipts and account statements are required as evidence for human"
                  + " review."
              : "Merchant acknowledgements and transaction receipts are the updated evidence"
                  + " required for review.";
      UUID id = UUID.randomUUID();
      String hash = com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(text);
      jdbc.update(
          "INSERT INTO"
              + " knowledge.document_registry(id,source_id,source_version,doc_type,classification,checksum,content_hash,chunk_count,ingested_by,raw_uri)"
              + " VALUES (?,'release-policy',?,'policy','C0',?,?,1,'test','test:synthetic')",
          id,
          version,
          hash,
          hash);
      jdbc.update(
          "INSERT INTO knowledge.vector_store(id,content,metadata) VALUES (?,?,'{}'::jsonb)",
          id,
          text);
      jdbc.update(
          "INSERT INTO knowledge.chunks_lineage(id,document_id,chunk_index,chunk_hash,vector_id)"
              + " VALUES (?,?,0,?,?)",
          id,
          id,
          hash,
          id);
      jdbc.update(
          "INSERT INTO knowledge.corpus_versions(run_id,document_count,chunk_count) VALUES (?,1,1)",
          version);
      jdbc.update("INSERT INTO knowledge.corpus_members VALUES (?,?)", version, id);
      jdbc.update(
          "INSERT INTO knowledge.trusted_sources(source_id,source_version,checksum,trusted_by)"
              + " VALUES ('release-policy',?,?,'test')",
          version,
          hash);
      index.index(version);
    }
    ReleaseEnvelope release;
    try (var input =
        new org.springframework.core.io.ClassPathResource("releases/release-a.json")
            .getInputStream()) {
      release = mapper.readValue(input, ReleaseEnvelope.class);
    }
    var account = users.loadUserByUsername("investigator");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                account, null, account.getAuthorities()));
    try {
      var query =
          new RetrievalRequest(
              "What transaction receipt evidence is required for review?",
              null,
              null,
              5,
              null,
              null);
      var latest = knowledge.retrieveCaseEvidence("release-pin-case", query);
      var pinned = knowledge.retrievePinnedCaseEvidence("release-pin-case", query, release);
      assertThat(latest.corpusVersion()).isEqualTo(2);
      assertThat(pinned.corpusVersion()).isEqualTo(1);
      assertThat(pinned.fixtureEmbeddings()).isFalse();
      assertThat(pinned.embeddingSpace()).isEqualTo(release.embeddingSpace());
      assertThat(pinned.retrievalConfigVersion()).isEqualTo(release.retrievalConfiguration());
      assertThat(pinned.passages())
          .isNotEmpty()
          .allSatisfy(p -> assertThat(p.citation().sourceVersion()).isEqualTo(1));
      jdbc.update(
          "UPDATE knowledge.trusted_sources SET revoked=true WHERE source_id='release-policy' AND"
              + " source_version=1");
      assertThatThrownBy(
              () -> knowledge.retrievePinnedCaseEvidence("release-pin-case", query, release))
          .isInstanceOf(com.fraudengine.platform.api.AccessDeniedException.class);
      Files.createDirectories(Path.of("target/week34"));
      mapper
          .writerWithDefaultPrettyPrinter()
          .writeValue(
              Path.of("target/week34/pinned-native-retrieval.json").toFile(),
              Map.of(
                  "_purpose",
                  "Actual native corpus pin and fresh source revocation check",
                  "defaultCorpus",
                  latest.corpusVersion(),
                  "pinnedResult",
                  pinned,
                  "revokedSourceDenied",
                  true));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @AfterAll
  static void close() {
    new TestContextManager(PinnedNativeRetrievalTest.class)
        .getTestContext()
        .markApplicationContextDirty(
            org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);
  }
}
