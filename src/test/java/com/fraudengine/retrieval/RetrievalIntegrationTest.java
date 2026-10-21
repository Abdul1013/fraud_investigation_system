/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/retrieval/RetrievalIntegrationTest.java
 * CONTEXT:     knowledge
 * LAYER:       test
 * PURPOSE:     Verify knowledge behavior against PostgreSQL and authenticated HTTP.
 * ----------------------------------------------------------------------------
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-27
 * RELATED:     ADR-005, Week 27 retrieval, Week 28 authentication
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Testcontainers isolate destructive cleanup from user databases.
 *   - HTTP requests authenticate as test-only curator and obtain real CSRF tokens.
 *   - Production filters remain enabled so prior validation and conflict behavior is checked.
 * ============================================================================
 */
package com.fraudengine.retrieval;

import static org.assertj.core.api.Assertions.*;

import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.knowledge.retrieval.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "knowledge.retrieval.embedding-provider=fixture",
            "knowledge.retrieval.answer-provider=extractive"
        })
@ActiveProfiles("test")
@Testcontainers
class RetrievalIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        com.fraudengine.platform.security.AuthenticatedHttp.accounts(properties);
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate rawHttp;
    com.fraudengine.platform.security.AuthenticatedHttp http;
    @Autowired RetrievalIndexService index;
    @Autowired RetrievalService retrieval;
    @Autowired AnswerService answers;
    @Autowired RetrievalEvaluator evaluator;
    @Autowired com.fraudengine.cases.api.CaseApi cases;
    @Autowired com.fraudengine.knowledge.api.KnowledgeApi caseKnowledge;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.fraudengine.platform.gateway.RetrievalEmbeddingPort queryEmbeddings;
    final UUID policy = UUID.nameUUIDFromBytes("policy".getBytes()),
            newPolicy = UUID.nameUUIDFromBytes("newPolicy".getBytes());

    @BeforeEach
    void seed() {
        http = new com.fraudengine.platform.security.AuthenticatedHttp(rawHttp);
        jdbc.execute("TRUNCATE case_management.case_sources, case_management.case_grants, case_management.case_records");
        jdbc.execute(
                "TRUNCATE"
                    + " knowledge.retrieval_embeddings,knowledge.retrieval_spaces,knowledge.ingestion_runs,knowledge.corpus_members,knowledge.corpus_versions,knowledge.ingestion_failures,knowledge.ingestion_sources,knowledge.chunks_lineage,knowledge.document_registry,knowledge.vector_store"
                    + " RESTART IDENTITY");
        add(
                policy,
                "chargeback",
                1,
                "C0",
                "## Evidence\nCollect transaction receipts and delivery confirmation.");
        add(
                newPolicy,
                "chargeback",
                2,
                "C0",
                "## Evidence\nNew evidence policy: collect merchant acknowledgements.");
        UUID confidential = UUID.nameUUIDFromBytes("confidential".getBytes());
        add(
                confidential,
                "confidential-case",
                1,
                "C2",
                "## Evidence\nTransaction receipts reveal a confidential case.");
        UUID restricted = UUID.nameUUIDFromBytes("restricted".getBytes());
        add(restricted, "restricted-kyc", 1, "C3", "## Evidence\nTransaction receipts SECRET-KYC.");
        UUID unpublished = UUID.nameUUIDFromBytes("unpublished".getBytes());
        add(unpublished, "unpublished", 1, "C0", "## Evidence\nTransaction receipts UNPUBLISHED.");
        jdbc.update(
                "INSERT INTO knowledge.corpus_versions(run_id,document_count,chunk_count) VALUES"
                        + " (1,3,3),(2,1,1)");
        for (UUID id : List.of(policy, confidential, restricted))
            jdbc.update("INSERT INTO knowledge.corpus_members VALUES (1,?)", id);
        jdbc.update("INSERT INTO knowledge.corpus_members VALUES (2,?)", newPolicy);
    }

    void caseFixture(String ceiling, String... sources) {
        jdbc.update("INSERT INTO case_management.case_records(case_id,status,summary,classification)"
                + " VALUES ('case-1','INVESTIGATING','Synthetic case summary','C0')");
        jdbc.update("INSERT INTO case_management.case_grants VALUES ('case-1','investigator',?)", ceiling);
        for (String source : sources) {
            jdbc.update("INSERT INTO case_management.case_sources VALUES ('case-1',?)", source);
        }
    }

    @Test
    void persistedCaseGrantIsRequiredAndRevocationTakesEffectOnNextRequest() {
        caseFixture("C2", "chargeback");
        var investigator = new com.fraudengine.platform.security.AuthenticatedHttp(rawHttp, "investigator");
        var analyst = new com.fraudengine.platform.security.AuthenticatedHttp(rawHttp, "analyst");
        assertThat(rawHttp.getForEntity("/v1/cases/case-1", String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(investigator.getForEntity("/v1/cases/case-1", String.class).getStatusCode().value()).isEqualTo(200);
        var denied = analyst.getForEntity("/v1/cases/case-1", Map.class);
        var missing = analyst.getForEntity("/v1/cases/missing", Map.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        assertThat(missing.getStatusCode().value()).isEqualTo(403);
        assertThat(denied.getBody()).isEqualTo(missing.getBody()).containsEntry("error", "Access denied");
        jdbc.update("DELETE FROM case_management.case_grants WHERE case_id='case-1'");
        assertThat(investigator.getForEntity("/v1/cases/case-1", String.class).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void summaryClassificationCannotExceedGrantClearance() {
        caseFixture("C0", "chargeback");
        jdbc.update("UPDATE case_management.case_records SET classification='C2', summary='SECRET-SUMMARY'");
        var investigator = new com.fraudengine.platform.security.AuthenticatedHttp(rawHttp, "investigator");
        var denied = investigator.getForEntity("/v1/cases/case-1", String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        assertThat(denied.getBody()).doesNotContain("SECRET-SUMMARY");
    }

    @Test
    void caseSourceScopeAppliesBeforeCompletenessAndBothRankingStrategies() {
        caseFixture("C2", "chargeback");
        index.index(1);
        // Missing embeddings outside the case scope must neither fail nor enter candidate ranking.
        jdbc.update("DELETE FROM knowledge.retrieval_embeddings WHERE chunk_id<>?", policy);
        var investigator = new com.fraudengine.platform.security.AuthenticatedHttp(rawHttp, "investigator");
        for (var strategy : RetrievalRequest.Strategy.values()) {
            var result = investigator.postForEntity("/v1/cases/case-1/evidence", request(1, strategy), RetrievalResult.class);
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody().passages()).isNotEmpty().allSatisfy(passage ->
                    assertThat(passage.citation().sourceId()).isEqualTo("chargeback"));
        }
    }

    @Test
    void callerAndServerClearancesIntersectForBothStrategies() {
        caseFixture("C0", "chargeback", "confidential-case", "restricted-kyc");
        index.index(1);
        var investigator = new com.fraudengine.platform.security.AuthenticatedHttp(rawHttp, "investigator");
        for (var strategy : RetrievalRequest.Strategy.values()) {
            var result = investigator.postForEntity("/v1/cases/case-1/evidence", request(1, strategy), RetrievalResult.class);
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody().passages()).isNotEmpty().allSatisfy(passage ->
                    assertThat(passage.citation().classification()).isEqualTo("C0"));
            var publicServer = new RetrievalService(new RetrievalRepository(jdbc), queryEmbeddings, "C0", 0.15);
            assertThat(publicServer.retrieveScoped(request(1, strategy), "C2",
                    Set.of("chargeback", "confidential-case")).passages())
                    .isNotEmpty().allSatisfy(passage ->
                            assertThat(passage.citation().classification()).isEqualTo("C0"));
        }
    }

    @Test
    void unrelatedCaseAndEmptyAllowlistNeverInvokeQueryEmbedding() {
        caseFixture("C2");
        org.mockito.Mockito.clearInvocations(queryEmbeddings);
        var analyst = new com.fraudengine.platform.security.AuthenticatedHttp(rawHttp, "analyst");
        assertThat(analyst.postForEntity("/v1/cases/case-1/evidence", request(1, null), String.class)
                .getStatusCode().value()).isEqualTo(403);
        var investigator = new com.fraudengine.platform.security.AuthenticatedHttp(rawHttp, "investigator");
        var result = investigator.postForEntity("/v1/cases/case-1/evidence", request(1, null), RetrievalResult.class);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().passages()).isEmpty();
        org.mockito.Mockito.verify(queryEmbeddings, org.mockito.Mockito.never()).embed(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void directPublicApiCallsCannotBypassAuthenticationOrValidation() {
        caseFixture("C2", "chargeback");
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> cases.getCase("case-1"))
                .isInstanceOf(com.fraudengine.platform.api.AccessDeniedException.class);
        assertThatThrownBy(() -> caseKnowledge.retrieveCaseEvidence("case-1", request(1, null)))
                .isInstanceOf(com.fraudengine.platform.api.AccessDeniedException.class);
        assertThatThrownBy(() -> caseKnowledge.retrieveCaseEvidence("case-1",
                new RetrievalRequest("", 1, null, 1000, null, null)))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    void add(UUID id, String source, int version, String classification, String content) {
        String hash =
                com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(content);
        jdbc.update(
                "INSERT INTO"
                    + " knowledge.document_registry(id,source_id,source_version,doc_type,classification,checksum,content_hash,chunk_count,ingested_by,raw_uri)"
                    + " VALUES (?,?,?,'policy',?,?,?,1,'test','test:source')",
                id,
                source,
                version,
                classification,
                hash,
                hash);
        jdbc.update(
                "INSERT INTO knowledge.vector_store(id,content,metadata) VALUES (?,?,?::jsonb)",
                id,
                content,
                "{}");
        jdbc.update(
                "INSERT INTO"
                    + " knowledge.chunks_lineage(id,document_id,chunk_index,chunk_hash,vector_id)"
                    + " VALUES (?,?,0,?,?)",
                id,
                id,
                hash,
                id);
    }

    RetrievalRequest request(int corpus, RetrievalRequest.Strategy strategy) {
        return new RetrievalRequest(
                "transaction receipts evidence", corpus, strategy, 5, null, null);
    }

    @Test
    void vectorAndHybridHonorSnapshotAndNeverReturnRestrictedOrUnpublishedSources() {
        assertThat(index.index(1).created()).isEqualTo(2);
        assertThat(index.index(1).reused()).isEqualTo(2);
        for (var strategy : RetrievalRequest.Strategy.values()) {
            var found = retrieval.retrieve(request(1, strategy));
            assertThat(found.passages())
                    .isNotEmpty()
                    .allSatisfy(
                            p -> {
                                assertThat(p.citation().classification()).isNotEqualTo("C3");
                                assertThat(p.citation().sourceVersion()).isEqualTo(1);
                                assertThat(p.text())
                                        .doesNotContain(
                                                "UNPUBLISHED", "SECRET-KYC", "New evidence");
                            });
        }
        index.index(2);
        var latest =
                retrieval.retrieve(
                        new RetrievalRequest(
                                "merchant acknowledgements", null, null, 5, null, null));
        assertThat(latest.corpusVersion()).isEqualTo(2);
        assertThat(latest.passages())
                .allSatisfy(p -> assertThat(p.citation().sourceVersion()).isEqualTo(2));
    }

    @Test
    void concurrentIndexReportsCountOnlyNewlyCommittedRows() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> index.index(1));
            var b = pool.submit(() -> index.index(1));
            var one = a.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var two = b.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(one.created() + two.created()).isEqualTo(2);
            assertThat(one.reused() + two.reused()).isEqualTo(2);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM knowledge.retrieval_embeddings",
                                    Integer.class))
                    .isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void effectiveDateFiltersApplyToBothStrategiesButIndexIncludesFutureSources() {
        jdbc.update(
                "UPDATE knowledge.document_registry SET effective_from='2030-01-01' WHERE id=?",
                policy);
        assertThat(index.index(1).created()).isEqualTo(2);
        for (var strategy : RetrievalRequest.Strategy.values()) {
            var early =
                    retrieval.retrieve(
                            new RetrievalRequest(
                                    "transaction receipts",
                                    1,
                                    strategy,
                                    5,
                                    null,
                                    LocalDate.of(2026, 1, 1)));
            assertThat(early.passages())
                    .noneSatisfy(p -> assertThat(p.citation().sourceId()).isEqualTo("chargeback"));
            var later =
                    retrieval.retrieve(
                            new RetrievalRequest(
                                    "delivery confirmation",
                                    1,
                                    strategy,
                                    5,
                                    null,
                                    LocalDate.of(2031, 1, 1)));
            assertThat(later.passages())
                    .anySatisfy(p -> assertThat(p.citation().sourceId()).isEqualTo("chargeback"));
        }
    }

    @Test
    void serverControlledPublicCeilingCannotBeRaisedByCaller() {
        var publicService =
                new RetrievalService(
                        new RetrievalRepository(jdbc),
                        new com.fraudengine.platform.gateway.provider.FixtureRetrievalEmbedding(),
                        "C0",
                        0.15);
        index.index(1);
        for (var strategy : RetrievalRequest.Strategy.values())
            assertThat(publicService.retrieve(request(1, strategy)).passages())
                    .allSatisfy(p -> assertThat(p.citation().classification()).isEqualTo("C0"));
    }

    @Test
    void missingIndexAndInvalidRequestsAreVisibleHttpErrors() {
        assertThat(
                        http.postForEntity("/v1/retrieve", request(1, null), String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(409);
        assertThat(
                        http.postForEntity(
                                        "/v1/retrieve",
                                        Map.of("query", "", "topK", 999),
                                        String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        assertThat(
                        http.postForEntity(
                                        "/v1/knowledge/retrieval/index",
                                        Map.of("corpusVersion", 1),
                                        String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        var answer =
                http.postForEntity("/v1/knowledge/answers", request(1, null), AnswerResult.class);
        assertThat(answer.getStatusCode().value()).isEqualTo(200);
        assertThat(answer.getBody().status()).isEqualTo("ANSWERED");
        assertThat(answer.getBody().evidence()).isNotEmpty();
    }

    @Test
    void corruptedEvidenceIsRejectedAndUnrelatedQuestionsAbstain() {
        index.index(1);
        assertThat(
                        answers.answer(
                                        new RetrievalRequest(
                                                "Neptune moons", 1, null, 5, null, null))
                                .status())
                .isEqualTo("INSUFFICIENT_EVIDENCE");
        jdbc.update(
                "UPDATE knowledge.vector_store SET content='Transaction receipts have been"
                        + " altered.' WHERE id=?",
                policy);
        assertThat(answers.answer(request(1, null)).status()).isEqualTo("INVALID_EVIDENCE");
    }

    @Test
    void oldModelSpaceDoesNotSatisfyNewIndexAndWrongDimensionIsRejected() {
        index.index(1);
        var repository = new RetrievalRepository(jdbc);
        assertThatThrownBy(
                        () ->
                                repository.requireComplete(
                                        1, null, LocalDate.now(), List.of("C0"), "another-model"))
                .isInstanceOf(IndexUnavailableException.class);
        assertThatThrownBy(() -> repository.requireDimensions("fixture:token-hash-v2:1536", 768))
                .isInstanceOf(IndexUnavailableException.class);
    }

    @Test
    void evaluationWritesComparableReportsWithVersionEvidence() throws Exception {
        index.index(1);
        Path dataset = Path.of("target/test-reports/week27-small-dataset.json");
        Files.createDirectories(dataset.getParent());
        Files.writeString(
                dataset,
                """
                [{"id":"evidence","query":"transaction receipts","unknown":false,"relevant":[{"sourceId":"chargeback","sourceVersion":1,"contains":"Collect transaction receipts"}]},
                {"id":"unknown","query":"Neptune moons","unknown":true,"relevant":[]}]
                """);
        var report = evaluator.evaluate(dataset, 1, 5, Path.of("target/test-reports/week27"));
        assertThat(report.fixtureEmbeddings()).isTrue();
        assertThat(report.strategies()).hasSize(2);
        assertThat(report.strategies())
                .allSatisfy(
                        s -> {
                            assertThat(s.recallAtK()).isEqualTo(1);
                            assertThat(s.quoteSupportRate()).isEqualTo(1);
                            assertThat(s.unknownAbstentionRate()).isEqualTo(1);
                        });
        assertThat(Files.readString(Path.of("target/test-reports/week27/retrieval-comparison.md")))
                .contains("VECTOR", "HYBRID", "fixture=true");
    }
}
