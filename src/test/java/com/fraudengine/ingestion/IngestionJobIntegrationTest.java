package com.fraudengine.ingestion;

import com.fraudengine.knowledge.ingestion.batch.IngestionBatchReader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.embedding.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class IngestionJobIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired Job knowledgeIngestionJob;
    @Autowired JobRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    @MockitoSpyBean EmbeddingModel embeddingModel;
    @TempDir Path corpus;
    TaskExecutorJobLauncher launcher;
    @BeforeEach void setup() throws Exception {
        launcher = new TaskExecutorJobLauncher(); launcher.setJobRepository(repository); launcher.afterPropertiesSet();
        reset(embeddingModel);
        jdbc.execute("TRUNCATE knowledge.ingestion_runs, knowledge.corpus_members, knowledge.corpus_versions, " +
                "knowledge.ingestion_failures,knowledge.ingestion_sources,knowledge.chunks_lineage,"+
                "knowledge.document_registry,knowledge.vector_store RESTART IDENTITY");
        source("policies/a-v1.md","## Eligibility\nPublic rules.\n## Evidence\nCollect a receipt.");
        source("policies/b-v1.md","## Resolution\nEscalate after 30 days.");
        source("kyc/customer.md","**Classification:** C3\nSECRET-KYC-SENTINEL");
    }
    void source(String name, String text) throws Exception {
        Path path=corpus.resolve(name); Files.createDirectories(path.getParent()); Files.writeString(path,text);
    }
    JobParameters parameters(long run) {
        return new JobParametersBuilder().addString("corpusRoot",corpus.toString()).addLong("run",run).toJobParameters();
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM knowledge."+table,Integer.class); }
    String report(JobExecution execution) {
        return jdbc.queryForObject("SELECT report_json::text FROM knowledge.ingestion_runs WHERE run_id=?",String.class,execution.getId());
    }
    @Test void realDatabaseIngestionProducesVectorsLineageSnapshotAndReport() throws Exception {
        var execution=launcher.run(knowledgeIngestionJob,parameters(1));
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("document_registry")).isEqualTo(3);
        assertThat(count("vector_store")).isEqualTo(3);
        assertThat(count("chunks_lineage")).isEqualTo(3);
        assertThat(count("corpus_versions")).isEqualTo(1);
        assertThat(count("corpus_members")).isEqualTo(3);
        assertThat(report(execution)).contains("\"documentsProcessed\": 3","\"chunksCreated\": 3","\"classification\": \"C3\"","\"chunkIds\": []");
        assertThat(Files.readString(Path.of("target/test-reports/ingestion-run-"+execution.getId()+".json"))).contains("COMPLETED");
        var requests=mockingDetails(embeddingModel).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("call"))
                .map(i -> ((EmbeddingRequest)i.getArgument(0)).getInstructions().toString()).toList();
        assertThat(requests).isNotEmpty().allSatisfy(text -> assertThat(text).doesNotContain("SECRET-KYC-SENTINEL"));
    }
    @Test void repeatedInputCreatesNoDuplicatesAndKeepsOldVersion() throws Exception {
        var first=launcher.run(knowledgeIngestionJob,parameters(2));
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        clearInvocations(embeddingModel);
        var second=launcher.run(knowledgeIngestionJob,parameters(3));
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("vector_store")).isEqualTo(3);
        assertThat(report(second)).contains("\"chunksCreated\": 0","\"chunksSkippedDuplicate\": 3");
        verify(embeddingModel,never()).call(any(EmbeddingRequest.class));
        source("policies/a-v2.md","## Eligibility\nNew reviewed rules.");
        var third=launcher.run(knowledgeIngestionJob,parameters(4));
        assertThat(third.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("document_registry")).isEqualTo(4);
        assertThat(count("vector_store")).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM knowledge.corpus_members m JOIN knowledge.document_registry d ON d.id=m.document_id
                WHERE m.corpus_version=3 AND d.source_id='policies/a' AND d.source_version=2
                """,Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge.corpus_members WHERE corpus_version=1",Integer.class)).isEqualTo(3);
    }
    @Test void transientFailureRetriesWithoutDuplicateSideEffects() throws Exception {
        var failures=new AtomicInteger();
        doAnswer(invocation -> {
            if (failures.getAndIncrement()<1) throw new org.springframework.ai.retry.TransientAiException("temporary fixture failure");
            return invocation.callRealMethod();
        }).when(embeddingModel).call(any(EmbeddingRequest.class));
        var execution=launcher.run(knowledgeIngestionJob,parameters(5));
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(failures.get()).isGreaterThan(1);
        assertThat(count("vector_store")).isEqualTo(3);
        assertThat(count("chunks_lineage")).isEqualTo(3);
    }
    @Test void failedJobRestartsAtCommittedBoundaryAndNeverPublishesFailure() throws Exception {
        doAnswer(invocation -> {
            EmbeddingRequest request=invocation.getArgument(0);
            if (request.getInstructions().stream().anyMatch(s -> s.contains("Resolution")))
                throw new org.springframework.ai.retry.TransientAiException("simulated outage");
            return invocation.callRealMethod();
        }).when(embeddingModel).call(any(EmbeddingRequest.class));
        var params=parameters(6);
        var failed=launcher.run(knowledgeIngestionJob,params);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(count("corpus_versions")).isZero();
        assertThat(count("vector_store")).isEqualTo(2);
        assertThat(report(failed)).contains("FAILED","simulated outage");
        reset(embeddingModel);
        var resumed=launcher.run(knowledgeIngestionJob,params);
        assertThat(resumed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(resumed.getJobInstance().getInstanceId()).isEqualTo(failed.getJobInstance().getInstanceId());
        assertThat(count("vector_store")).isEqualTo(3);
        assertThat(count("corpus_versions")).isEqualTo(1);
        assertThat(report(resumed)).contains("\"documentsProcessed\": 3","\"chunksCreated\": 3");
    }
    @Test void malformedSourceIsReportedAndPartialRunIsNotPromoted() throws Exception {
        source("policies/malformed.md","## Huge table\n| Column |\n|---|\n"+"| oversize table content |\n".repeat(1000));
        var execution=launcher.run(knowledgeIngestionJob,parameters(7));
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(report(execution)).contains("PARTIAL","policies/malformed","Table section");
        assertThat(count("corpus_versions")).isZero();
    }
    @Test void emptyReadIsSkippedWithSourceIdentity() throws Exception {
        source("policies/empty.md", "");
        var execution=launcher.run(knowledgeIngestionJob,parameters(70));
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(report(execution)).contains("PARTIAL","policies/empty","Empty source");
        assertThat(count("corpus_versions")).isZero();
        assertThat(count("vector_store")).isEqualTo(3);
    }
    @Test void changedRevisionIsRejectedWithoutOverwritingExistingEvidence() throws Exception {
        assertThat(launcher.run(knowledgeIngestionJob,parameters(8)).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        source("policies/a-v1.md","## Changed\nA conflicting revision.");
        var execution=launcher.run(knowledgeIngestionJob,parameters(9));
        assertThat(report(execution)).contains("PARTIAL","increment source_version");
        assertThat(count("vector_store")).isEqualTo(3);
        assertThat(count("corpus_versions")).isEqualTo(1);
    }
    @Test void policyClassifiedRestrictedIsRegisteredWithoutEmbedding() throws Exception {
        source("policies/restricted.md","**Classification:** C3\nSECRET-POLICY-SENTINEL");
        var execution=launcher.run(knowledgeIngestionJob,parameters(71));
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("document_registry")).isEqualTo(4);
        assertThat(count("vector_store")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT chunk_count FROM knowledge.document_registry WHERE source_id='policies/restricted'",Integer.class)).isZero();
        for (var invocation : mockingDetails(embeddingModel).getInvocations())
            if (invocation.getMethod().getName().equals("call"))
                assertThat(((EmbeddingRequest)invocation.getArgument(0)).getInstructions().toString()).doesNotContain("SECRET-POLICY-SENTINEL");
    }
    @Test void bundledCorpusProducesACompleteSampleReport() throws Exception {
        var params=new JobParametersBuilder().addString("corpusRoot","classpath:corpus/").addLong("run",72L).toJobParameters();
        var execution=launcher.run(knowledgeIngestionJob,params);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("document_registry")).isEqualTo(11);
        assertThat(count("vector_store")).isGreaterThan(11);
        assertThat(count("corpus_versions")).isEqualTo(1);
        assertThat(report(execution)).contains("COMPLETED","policies/chargeback-policy@3","kyc/kyc-sample-001@1");
        Files.writeString(Path.of("target/test-reports/sample-ingestion-run.json"),report(execution));
    }
    @Test void httpApiRunsInBackgroundAndExposesStatusWithConflictValidation() throws Exception {
        var start=http.postForEntity("/v1/knowledge/ingestions",Map.of("runKey","http-demo"),com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(start.getStatusCode().value()).isEqualTo(202);
        long runId=start.getBody().get("runId").asLong();
        com.fasterxml.jackson.databind.JsonNode status=null;
        for (int attempt=0;attempt<200;attempt++) {
            status=http.getForObject("/v1/knowledge/ingestions/"+runId,com.fasterxml.jackson.databind.JsonNode.class);
            if (status.get("status").asText().equals("COMPLETED") || status.get("status").asText().equals("FAILED")) break;
            Thread.sleep(25);
        }
        assertThat(status.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(status.get("report").get("documentsProcessed").asInt()).isEqualTo(11);
        assertThat(http.postForEntity("/v1/knowledge/ingestions",Map.of("runKey","http-demo"),String.class).getStatusCode().value()).isEqualTo(409);
        assertThat(http.postForEntity("/v1/knowledge/ingestions",Map.of("runKey","bad/path"),String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.postForEntity("/v1/knowledge/ingestions/"+runId+"/restart",null,String.class).getStatusCode().value()).isEqualTo(409);
        assertThat(http.getForEntity("/v1/knowledge/ingestions/9999999",String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForObject("/v1/knowledge/corpus/versions",com.fasterxml.jackson.databind.JsonNode.class)).hasSize(1);
        assertThat(http.getForObject("/actuator/health",com.fasterxml.jackson.databind.JsonNode.class).get("status").asText()).isEqualTo("UP");
    }
    @Test void concurrentIngestionUsesSourceLocksToAvoidDuplicates() throws Exception {
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one=workers.submit(() -> launcher.run(knowledgeIngestionJob,parameters(100)));
            var two=workers.submit(() -> launcher.run(knowledgeIngestionJob,parameters(101)));
            assertThat(one.get(20,java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(two.get(20,java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(count("document_registry")).isEqualTo(3);
            assertThat(count("vector_store")).isEqualTo(3);
            assertThat(count("chunks_lineage")).isEqualTo(3);
            assertThat(count("corpus_versions")).isEqualTo(2);
        } finally { workers.shutdownNow(); }
    }
    @Test void readerRejectsChangedManifestOnRestart() throws Exception {
        var context=new org.springframework.batch.item.ExecutionContext();
        var reader=new IngestionBatchReader(corpus.toString()); reader.open(context); reader.read(); reader.update(context);
        source("policies/new.md","## New\nAnother source.");
        assertThatThrownBy(() -> new IngestionBatchReader(corpus.toString()).open(context))
                .isInstanceOf(org.springframework.batch.item.ItemStreamException.class).hasMessageContaining("Corpus changed");
    }
}
