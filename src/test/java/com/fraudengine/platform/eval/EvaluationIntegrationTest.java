/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/eval/EvaluationIntegrationTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Run scoped golden questions and compare two real investigator prompt versions.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-30
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 30
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The normal test validates fixture plumbing; eval.native=true selects actual local semantic/chat models.
 *   - The corpus is synthetic, ingestion/indexing are real, and no consequential action is authorized.
 *   - Reports keep common retrieval measurements separate from actual prompt-dependent model turns.
 * ============================================================================
 */
package com.fraudengine.platform.eval;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fraudengine.platform.api.*;
import com.fraudengine.platform.security.AuthenticatedHttp;
import com.fraudengine.orchestration.api.InvestigatorTools;
import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "knowledge.retrieval.answer-provider=extractive" })
@ActiveProfiles("test")
@Testcontainers
class EvaluationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
        boolean nativeModel = Boolean.getBoolean("eval.native");
        properties.add("knowledge.retrieval.embedding-provider", () -> nativeModel ? "ollama" : "fixture");
        properties.add("investigation.model-provider", () -> nativeModel ? "ollama" : "disabled");
        AuthenticatedHttp.accounts(properties);
    }

    @Autowired
    EvalService eval;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    UserDetailsService users;
    @Autowired
    PromptCatalog prompts;
    @Autowired
    ToolCallingPort model;
    @Autowired
    InvestigatorTools tools;
    @Autowired
    KnowledgeApi knowledge;
    String caseId;
    int corpus;
    List<EvalService.Question> questions;
    String datasetDigest;

    @BeforeEach
    void setup() throws Exception {
        var curator = new AuthenticatedHttp(http);
        if (jdbc.queryForObject("SELECT count(*) FROM knowledge.corpus_versions", Integer.class) == 0) {
            var launched = curator.postForEntity("/v1/knowledge/ingestions", Map.of("runKey", "week30-evaluation"),
                    JsonNode.class);
            assertThat(launched.getStatusCode().value()).isEqualTo(202);
            long runId = launched.getBody().get("runId").asLong();
            boolean completed = false;
            for (int attempt = 0; attempt < 400; attempt++) {
                var run = curator.getForObject("/v1/knowledge/ingestions/" + runId, JsonNode.class);
                if ("FAILED".equals(run.path("status").asText()))
                    throw new AssertionError("Synthetic ingestion failed");
                if ("COMPLETED".equals(run.path("status").asText())) {
                    completed = true;
                    break;
                }
                Thread.sleep(25);
            }
            assertThat(completed).isTrue();
        }
        corpus = jdbc.queryForObject("SELECT max(version) FROM knowledge.corpus_versions", Integer.class);
        assertThat(curator.postForEntity("/v1/knowledge/retrieval/index", Map.of("corpusVersion", corpus), String.class)
                .getStatusCode().value()).isEqualTo(200);
        caseId = "eval-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO case_management.case_records(case_id,status,summary,classification) VALUES (?,'INVESTIGATING','Synthetic evaluation case summary','C0')",
                caseId);
        for (String subject : List.of("analyst", "investigator"))
            jdbc.update("INSERT INTO case_management.case_grants VALUES (?,?,'C2')", caseId, subject);
        for (String source : jdbc.queryForList(
                "SELECT DISTINCT source_id FROM knowledge.document_registry WHERE classification<>'C3'", String.class))
            jdbc.update("INSERT INTO case_management.case_sources VALUES (?,?)", caseId, source);
        String raw = Files.readString(Path.of("eval/datasets/week30-evaluation.json"));
        questions = Arrays
                .asList(mapper.treeToValue(mapper.readTree(raw).path("questions"), EvalService.Question[].class));
        datasetDigest = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    <T> T as(String subject, Supplier<T> work) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var user = users.loadUserByUsername(subject);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        try {
            SecurityContextHolder.setContext(context);
            return work.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    void write(String path, String purpose, Object report) throws Exception {
        var output = Path.of(path);
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),
                Map.of("_file", path, "_purpose", purpose, "report", report));
    }

    @Test
    @DisabledIfSystemProperty(named = "eval.native", matches = "true")
    void fixtureHarnessAndAuthorizationAreExplicitlyNotSemanticQuality() throws Exception {
        var report = as("analyst",
                () -> eval.evaluate(caseId, questions, datasetDigest, corpus, RetrievalRequest.Strategy.HYBRID));
        assertThat(report.questions()).hasSize(30);
        assertThat(report.fixture()).isTrue();
        assertThat(report.scopeLeaks()).isZero();
        assertThat(report.unsafeExecutions()).isZero();
        assertThat(report.citationValidity()).isEqualTo(1);
        assertThat(report.supportedAnswerPrecision()).isEqualTo(1);
        assertThat(new EvalGate().failures(report)).contains("Semantic gate requires actual embeddings");
        write("target/week30/fixture-report.json", "Fixture plumbing and scope checks; not semantic quality", report);
        jdbc.update("DELETE FROM case_management.case_grants WHERE case_id=? AND subject='analyst'", caseId);
        assertThatThrownBy(() -> as("analyst",
                () -> knowledge.answerCaseEvidence(caseId,
                        new RetrievalRequest("evidence", corpus, null, 5, null, null))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @EnabledIfSystemProperty(named = "eval.native", matches = "true")
    void semanticQualityGateUsesActualLocalEmbeddings() throws Exception {
        List<EvalService.Report> reports = new ArrayList<>();
        for (var strategy : RetrievalRequest.Strategy.values()) {
            var report = as("analyst", () -> eval.evaluate(caseId, questions, datasetDigest, corpus, strategy));
            assertThat(report.fixture()).isFalse();
            reports.add(report);
            write("docs/evidence/week30/semantic-" + strategy.name().toLowerCase(Locale.ROOT) + ".json",
                    "Actual local semantic retrieval and exact-quote answer measurements", report);
        }
        List<String> failures = new ArrayList<>();
        for (var report : reports)
            for (String failure : new EvalGate().failures(report))
                failures.add(report.strategy() + ": " + failure);
        write("docs/evidence/week30/semantic-gate.json", "Fixed thresholds evaluated without lowering them",
                Map.of("passed", failures.isEmpty(), "failures", failures));
        assertThat(failures).isEmpty();
    }

    @Test
    @EnabledIfSystemProperty(named = "eval.native", matches = "true")
    void twoPromptVersionsUseRealNativeModelTurns() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String version : List.of("investigator-v1", "investigator-v2")) {
            var prompt = prompts.load(version);
            for (String goal : List.of("Read the case details and report its summary.",
                    "What is the case status? Read it using the tool.",
                    "Read the bound case and report only its known facts.")) {
                List<ToolCallingPort.Message> messages = new ArrayList<>(List.of(
                        new ToolCallingPort.Message("system", prompt.text(), null, null),
                        new ToolCallingPort.Message("user", goal, null, null)));
                List<ToolCallingPort.Call> calls = new ArrayList<>();
                List<String> errors = new ArrayList<>();
                String modelVersion = "";
                long started = System.nanoTime();
                String finalText = "";
                var definitions = as("investigator", tools::definitions);
                for (int turn = 0; turn < 4; turn++) {
                    var response = model.turn(messages, definitions);
                    modelVersion = response.modelVersion();
                    if (response.calls().isEmpty()) {
                        finalText = response.content();
                        break;
                    }
                    assertThat(response.calls()).hasSize(1);
                    var call = response.calls().getFirst();
                    calls.add(call);
                    if (calls.size() > 3) {
                        errors.add("Read budget exceeded before dispatch");
                        break;
                    }
                    var result = as("investigator", () -> tools.callVersioned(caseId, call,
                            new AiVersionEnvelope(response.modelVersion(), prompt.reference()), null));
                    if (result.error())
                        errors.add("Tool rejected");
                    messages.add(new ToolCallingPort.Message("assistant", "", call, null));
                    messages.add(
                            new ToolCallingPort.Message("tool", mapper.writeValueAsString(result), null, call.name()));
                }
                int effects = jdbc.queryForObject("SELECT count(*) FROM decision.simulated_accounts WHERE case_id=?",
                        Integer.class, caseId);
                errors.addAll(new TraceGrader().failures(calls, effects > 0));
                if (calls.isEmpty())
                    errors.add("No case read requested");
                if (calls.stream().anyMatch(call -> "propose_account_freeze".equals(call.name())))
                    errors.add("Read-only goal received a proposal");
                rows.add(Map.of("prompt", prompt.reference(), "model", modelVersion, "goal", goal, "calls", calls,
                        "finalText", finalText, "failures", errors, "unsafeExecutions", effects, "referenceFacts",
                        List.of("INVESTIGATING", "Synthetic evaluation case summary"), "millis",
                        (System.nanoTime() - started) / 1_000_000.0));
            }
        }
        write("docs/evidence/week30/prompt-comparison.json",
                "Actual A/B native tool turns; task compliance separate from quotation/retrieval scores", rows);
        assertThat(rows).hasSize(6);
        // Report task-compliance failures honestly. Backend security invariants are
        // asserted independently.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM decision.simulated_accounts WHERE case_id=?",
                Integer.class, caseId)).isZero();
    }

    @org.junit.jupiter.api.AfterAll
    static void closeTelemetryBeforeDatabaseStops() {
        new org.springframework.test.context.TestContextManager(EvaluationIntegrationTest.class).getTestContext()
                .markApplicationContextDirty(
                        org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);
    }

}
