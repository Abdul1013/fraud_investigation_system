/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/knowledge/retrieval/PerformanceHarnessTest.java
 * CONTEXT:     knowledge
 * LAYER:       test
 * PURPOSE:     Capture reproducible k6 evidence before and after performance changes.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - perf.run=true is explicit; fixture infrastructure and real semantic-model workloads are separate runs.
 *   - Authentication, CSRF, PostgreSQL and HTTP are real; the corpus and accounts are synthetic learning fixtures.
 *   - The fixed two-user 20-second repeated-query workload measures a warm read path, not production capacity.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fraudengine.platform.gateway.RetrievalEmbeddingPort;
import com.fraudengine.platform.security.AuthenticatedHttp;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"knowledge.trust.required=true","platform.admission.maximum-concurrent-reads=4","platform.rate-limit.reads-per-minute=10000"})
@ActiveProfiles("test") @Testcontainers
@EnabledIfSystemProperty(named="perf.run",matches="true")
class PerformanceHarnessTest {
    @Container static final PostgreSQLContainer<?> DATABASE=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",DATABASE::getJdbcUrl);properties.add("spring.datasource.username",DATABASE::getUsername);properties.add("spring.datasource.password",DATABASE::getPassword);
        properties.add("knowledge.retrieval.embedding-provider",()->Boolean.getBoolean("perf.native")?"ollama":"fixture");
        properties.add("knowledge.cache.enabled",()->Boolean.getBoolean("perf.optimized"));AuthenticatedHttp.accounts(properties);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired MeterRegistry meters;
    @MockitoSpyBean RetrievalEmbeddingPort embeddings;
    @LocalServerPort int port;
    double counter(String name,String...tags) {var found=meters.find(name).tags(tags).counter();return found==null?0:found.count();}
    @Test void fixedWarmAuthorizedReadWorkload() throws Exception {
        String text="The transaction receipts and account statement are required as evidence for human review.";
        String hash=com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(text);UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO case_management.case_records(case_id,status,summary,classification) VALUES ('perf-case','INVESTIGATING','Synthetic benchmark case','C0')");
        jdbc.update("INSERT INTO case_management.case_grants VALUES ('perf-case','investigator','C2')");jdbc.update("INSERT INTO case_management.case_sources VALUES ('perf-case','performance-policy')");
        jdbc.update("INSERT INTO knowledge.document_registry(id,source_id,source_version,doc_type,classification,checksum,content_hash,chunk_count,ingested_by,raw_uri) VALUES (?,'performance-policy',1,'policy','C0',?,?,1,'test','test:fixture')",id,hash,hash);
        jdbc.update("INSERT INTO knowledge.vector_store(id,content,metadata) VALUES (?,?,'{}'::jsonb)",id,text);
        jdbc.update("INSERT INTO knowledge.chunks_lineage(id,document_id,chunk_index,chunk_hash,vector_id) VALUES (?,?,0,?,?)",id,id,hash,id);
        jdbc.update("INSERT INTO knowledge.corpus_versions(run_id,document_count,chunk_count) VALUES (1,1,1)");jdbc.update("INSERT INTO knowledge.corpus_members VALUES (1,?)",id);
        jdbc.update("INSERT INTO knowledge.trusted_sources(source_id,source_version,checksum,trusted_by) VALUES ('performance-policy',1,?,'fixture-curator')",hash);
        assertThat(new AuthenticatedHttp(http).postForEntity("/v1/knowledge/retrieval/index",Map.of("corpusVersion",1),String.class).getStatusCode().value()).isEqualTo(200);
        var investigator=new AuthenticatedHttp(http,"investigator");
        assertThat(investigator.postForEntity("/v1/cases/perf-case/evidence",Map.of("query","What transaction receipt evidence is required for human review?","corpusVersion",1,"strategy","VECTOR","limit",5),String.class).getStatusCode().value()).isEqualTo(200);
        double hitsBefore=counter("fraud.retrieval.cache","outcome","hit"),missesBefore=counter("fraud.retrieval.cache","outcome","miss");
        clearInvocations(embeddings);double tokensBefore=counter("fraud.model.tokens","direction","input","provider","local");double costBefore=counter("fraud.local.compute.estimated.usd");
        String kind=Boolean.getBoolean("perf.native")?"native":"fixture",phase=Boolean.getBoolean("perf.optimized")?"optimized":"baseline";
        Path raw=Path.of("target/week33-"+kind+"-"+phase+".json").toAbsolutePath();Files.createDirectories(raw.getParent());
        var command=new ProcessBuilder(System.getenv().getOrDefault("K6_BIN","k6"),"run","performance/week33-read-workload.js").redirectErrorStream(true).redirectOutput(Path.of("target/week33-k6-"+kind+"-"+phase+".log").toFile());
        var environment=command.environment();environment.put("PERF_URL","http://127.0.0.1:"+port);environment.put("PERF_AUTHORIZATION","Basic "+Base64.getEncoder().encodeToString(("investigator:"+AuthenticatedHttp.PASSWORD).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        environment.put("PERF_EXPECTED_EVIDENCE",text);environment.put("PERF_SUMMARY_PATH",raw.toString());
        var process=command.start();assertThat(process.waitFor(120,TimeUnit.SECONDS)).isTrue();
        JsonNode result=mapper.readTree(raw.toFile());
        long work=mockingDetails(embeddings).getInvocations().stream().filter(call->call.getMethod().getName().equals("embed")).count();
        Map<String,Object> report=new LinkedHashMap<>();report.put("_file","docs/evidence/week33/"+kind+"-"+phase+".json");report.put("_purpose","Actual fixed k6 warm-read workload; synthetic corpus and local learning authentication");
        report.put("kind",kind);report.put("phase",phase);report.put("embeddingSpace",embeddings.spaceId());report.put("fixtureEmbeddings",embeddings.fixture());report.put("durationSeconds",20);report.put("virtualUsers",2);report.put("datasetChecksum",hash);report.put("queryEmbeddingCalls",work);report.put("k6ExitCode",process.exitValue());
        report.put("measuredInputTokens",Boolean.getBoolean("perf.native")?counter("fraud.model.tokens","direction","input","provider","local")-tokensBefore:null);
        report.put("estimatedLocalComputeUsd",Boolean.getBoolean("perf.native")?counter("fraud.local.compute.estimated.usd")-costBefore:null);
        report.put("latencyMillis",result.path("metrics").path("evidence_latency").path("values"));report.put("failures",Map.of("rate",result.path("metrics").path("evidence_failures").path("values").path("rate").asDouble(),"failedRequests",result.path("metrics").path("evidence_failures").path("values").path("passes").asLong(),"successfulRequests",result.path("metrics").path("evidence_failures").path("values").path("fails").asLong()));report.put("throughput",result.path("metrics").path("evidence_requests").path("values"));report.put("quality",result.path("metrics").path("checks").path("values"));
        report.put("cacheHits",counter("fraud.retrieval.cache","outcome","hit")-hitsBefore);report.put("cacheMisses",counter("fraud.retrieval.cache","outcome","miss")-missesBefore);report.put("costScope","Model inference estimate only; excludes authentication, database and host costs.");
        Path output=Path.of("docs/evidence/week33",kind+"-"+phase+".json");Files.createDirectories(output.getParent());mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
        assertThat(process.exitValue()).isZero();assertThat(result.path("metrics").path("evidence_failures").path("values").path("rate").asDouble()).isZero();
    }
    @AfterAll static void closeBeforeContainersStop() {new TestContextManager(PerformanceHarnessTest.class).getTestContext().markApplicationContextDirty(org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);}
}
