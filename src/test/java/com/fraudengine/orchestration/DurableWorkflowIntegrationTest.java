/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/orchestration/DurableWorkflowIntegrationTest.java
 * CONTEXT:     orchestration
 * LAYER:       test
 * PURPOSE:     Prove persistent Temporal recovery, retries, delegation revocation, and action idempotency.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-29
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Temporal runs as a real restartable container backed by its on-container SQLite file, alongside isolated PostgreSQL.
 *   - Model replies and verified step-up are explicit test fixtures; the workflow service, SDK, database, and restart are real.
 *   - History is checked to exclude goal, rationale, evidence, and approval credentials.
 * ============================================================================
 */
package com.fraudengine.orchestration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.audit.api.AuditApi;
import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.decision.api.DecisionApi;
import com.fraudengine.decision.api.dto.*;
import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.orchestration.api.*;
import com.fraudengine.orchestration.domain.*;
import com.fraudengine.orchestration.infrastructure.*;
import com.fraudengine.platform.api.*;
import com.fraudengine.platform.security.AuthenticatedHttp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import io.temporal.client.*;
import io.temporal.worker.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "investigation.engine=temporal","investigation.worker-enabled=false","investigation.outbox-delay-ms=100",
    "knowledge.retrieval.embedding-provider=fixture","investigation.call-timeout-seconds=2",
    "decision.approval-key=dGVzdC1vbmx5LTMydGVzdC1vbmx5LTMydGVzdC1vbmx5LTMydGVzdC1vbmx5LTMy"
})
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@ActiveProfiles("test")
@Testcontainers
class DurableWorkflowIntegrationTest {
    @Container static final PostgreSQLContainer<?> DATABASE=new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    static final int TEMPORAL_PORT=freePort();
    static int freePort() {
        try(var socket=new java.net.ServerSocket(0)) { return socket.getLocalPort(); }
        catch(java.io.IOException error) { throw new IllegalStateException(error); }
    }
    @Container static final GenericContainer<?> TEMPORAL=new GenericContainer<>(DockerImageName.parse("temporalio/temporal:1.4.1"))
            .withCommand("server","start-dev","--ip","0.0.0.0","--db-filename","/tmp/temporal.db")
            .withExposedPorts(7233)
            .withCreateContainerCmdModifier(command->command.getHostConfig().withPortBindings(
                    new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindPort(TEMPORAL_PORT),
                            new com.github.dockerjava.api.model.ExposedPort(7233))))
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username",DATABASE::getUsername);
        properties.add("spring.datasource.password",DATABASE::getPassword);
        properties.add("investigation.temporal-address",()->TEMPORAL.getHost()+":"+TEMPORAL.getMappedPort(7233));
        AuthenticatedHttp.accounts(properties);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired UserDetailsService users;
    @Autowired DecisionApi decisions;
    @Autowired KnowledgeApi knowledge;
    @Autowired InvestigatorTools tools;
    @Autowired OrchestrationApi investigations;
    @Autowired ObjectMapper mapper;
    @Autowired WorkflowClient client;
    @Autowired DurableTurnActivities activities;
    @Autowired WorkflowOutboxDelivery delivery;
    @MockitoBean StepUpVerifier simulatedStepUp;
    @MockitoBean ToolCallingPort model;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.fraudengine.platform.api.ReleaseCatalog releases;
    String caseId;
    WorkerFactory factory;

    void startWorker() {
        factory=WorkerFactory.newInstance(client);
        var worker=factory.newWorker(TemporalConfig.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(InvestigationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        RuntimeException last=null;
        for(int attempt=0;attempt<10;attempt++) {
            try { factory.start(); return; }
            catch(io.grpc.StatusRuntimeException unavailable) {
                last=unavailable;
                try { Thread.sleep(200); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }
        }
        throw last;
    }
    @AfterAll static void closeBeforeContainersStop() {
        // Evict through Spring's test cache, not a raw context.close() that leaves a closed cached entry.
        new org.springframework.test.context.TestContextManager(DurableWorkflowIntegrationTest.class)
                .getTestContext().markApplicationContextDirty(org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);
    }
    @AfterEach void stopWorker() {
        // The prompt-pin scenario intentionally leaves a queued run. Revoke its test delegation
        // before another method installs a new model mock; durable history survives worker shutdown.
        if(caseId!=null)jdbc.update("UPDATE orchestration.worker_delegations SET revoked=true WHERE case_id=?",caseId);
        factory.shutdownNow(); factory.awaitTermination(5,TimeUnit.SECONDS);
    }
    @BeforeEach
    void seed() {
        startWorker();
        caseId = "durable-" + UUID.randomUUID();
        jdbc.update("INSERT INTO case_management.case_records(case_id,status,summary,classification) VALUES (?,'INVESTIGATING','Synthetic summary','C0')", caseId);
        for (String subject : List.of("investigator", "analyst", "auditor")) {
            jdbc.update("INSERT INTO case_management.case_grants VALUES (?,?,'C2')", caseId, subject);
        }
        jdbc.update("INSERT INTO case_management.case_sources VALUES (?,'policy-week28')", caseId);
        if (jdbc.queryForObject("SELECT count(*) FROM knowledge.corpus_versions", Integer.class) == 0) {
            UUID id = UUID.randomUUID();
            String content = "Transaction receipts show inconsistent evidence requiring human review.";
            String hash = com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(content);
            jdbc.update("INSERT INTO knowledge.document_registry(id,source_id,source_version,doc_type,classification,checksum,content_hash,chunk_count,ingested_by,raw_uri)"
                    + " VALUES (?,'policy-week28',1,'policy','C0',?,?,1,'test','test:fixture')", id, hash, hash);
            jdbc.update("INSERT INTO knowledge.vector_store(id,content,metadata) VALUES (?,?,'{}'::jsonb)", id, content);
            jdbc.update("INSERT INTO knowledge.chunks_lineage(id,document_id,chunk_index,chunk_hash,vector_id) VALUES (?,?,0,?,?)", id, id, hash, id);
            jdbc.update("INSERT INTO knowledge.corpus_versions(run_id,document_count,chunk_count) VALUES (1,1,1)");
            jdbc.update("INSERT INTO knowledge.corpus_members VALUES (1,?)", id);
            var indexed = new AuthenticatedHttp(http).postForEntity("/v1/knowledge/retrieval/index", Map.of("corpusVersion", 1), String.class);
            assertThat(indexed.getStatusCode().value()).isEqualTo(200);
        }
    }

    RetrievalResult evidence() {
        return as("investigator", () -> knowledge.retrieveCaseEvidence(caseId,
                new RetrievalRequest("transaction receipts evidence", 1, null, 5, null, null)));
    }

    RecommendationDto propose() {
        var evidence = evidence();
        return as("investigator", () -> decisions.propose(caseId, "Human review is required", evidence, "scripted-fixture:v1"));
    }

    <T> T as(String name, Supplier<T> work) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var user = users.loadUserByUsername(name);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        try { SecurityContextHolder.setContext(context); return work.get(); }
        finally { SecurityContextHolder.setContext(previous); }
    }


    ToolCallingPort.Response reply(String name,Map<String,Object> args) {
        return new ToolCallingPort.Response("",List.of(new ToolCallingPort.Call(UUID.randomUUID().toString(),name,args)),"scripted-fixture:v1");
    }
    OrchestrationApi.Run await(UUID id,String expected) throws Exception {
        // Allow cold worker/service startup; activity call and active-phase deadlines remain independently enforced.
        for(int attempt=0;attempt<900;attempt++) {
            var run=as("investigator",()->investigations.get(id));
            if(expected.equals(run.state()))return run;
            Thread.sleep(50);
        }
        throw new AssertionError("Durable run did not reach "+expected+"; current="+as("investigator",()->investigations.get(id)).state());
    }

    @Test void workerAndServiceRestartPreserveHumanWaitAndExactlyOneEffect() throws Exception {
        var modelTurns=new java.util.concurrent.atomic.AtomicInteger();
        when(model.turn(anyList(),anyList())).thenAnswer(invocation->{
            if(modelTurns.getAndIncrement()==0)return reply("get_case_details",Map.of());
            return reply("propose_account_freeze",Map.of("evidenceQuery","transaction receipts evidence","rationale","Review synthetic evidence"));
        });
        var launched=new AuthenticatedHttp(http,"investigator").postForEntity("/v1/investigations",
                Map.of("caseId",caseId,"goal","Synthetic durable recovery","idempotencyKey","recovery"),OrchestrationApi.Run.class);
        assertThat(launched.getStatusCode().value()).isEqualTo(202);
        var run=launched.getBody();
        var paused=await(run.id(),"PENDING_APPROVAL");
        int turns=modelTurns.get();
        factory.shutdownNow();factory.awaitTermination(5,TimeUnit.SECONDS);
        // Restart the same container: its SQLite history file survives the server process restart.
        org.testcontainers.DockerClientFactory.instance().client().restartContainerCmd(TEMPORAL.getContainerId()).exec();
        Thread.sleep(1500);
        startWorker();
        jdbc.update("UPDATE orchestration.launch_outbox SET delivered_at=NULL WHERE run_id=?",run.id());
        delivery.deliver();
        var token=as("analyst",()->decisions.approve(paused.recommendationId()));
        var result=as("analyst",()->decisions.execute(paused.recommendationId(),token.token(),"durable-once"));
        assertThat(result.frozen()).isTrue();
        delivery.deliver();
        for(int attempt=0;attempt<100;attempt++) {
            if("EXECUTED".equals(client.newWorkflowStub(InvestigationWorkflow.class,run.id().toString()).status()))break;
            Thread.sleep(50);
        }
        assertThat(client.newWorkflowStub(InvestigationWorkflow.class,run.id().toString()).status()).isEqualTo("EXECUTED");
        assertThat(modelTurns.get()).isEqualTo(turns);
        assertThat(jdbc.queryForObject("SELECT freeze_count FROM decision.simulated_accounts WHERE case_id=?",Integer.class,caseId)).isEqualTo(1);
        if(Boolean.getBoolean("ops.demo")) {
            String parent=jdbc.queryForObject("SELECT trace_parent FROM orchestration.investigations WHERE id=?",String.class,run.id());
            assertThat(parent).isNotNull();String trace=parent.split("-")[1];
            var actionAudit=jdbc.queryForList("SELECT event_type,trace_id FROM audit.action_events WHERE case_id=? ORDER BY sequence",caseId);
            assertThat(actionAudit).isNotEmpty().allSatisfy(event->assertThat(event.get("trace_id")).isEqualTo(trace));
            var destination=java.nio.file.Path.of("docs/evidence/week32/lifecycle-audit.json");java.nio.file.Files.createDirectories(destination.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(),Map.of("_file",destination.toString(),"_purpose","Real Temporal restart and correlated action audit; model and step-up are explicit fixtures","traceId",trace,"audit",actionAudit));
        }
        var history=client.fetchHistory(run.id().toString());
        var tree=mapper.readTree(history.toJson(true));
        assertPrivatePayloadsAbsent(tree,List.of("Synthetic durable recovery","Review synthetic evidence",token.token()));
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history,InvestigationWorkflowImpl.class);
        var output=java.nio.file.Path.of("target/week29-recovery-history.json");
        java.nio.file.Files.writeString(output,history.toJson(true));
    }

    void assertPrivatePayloadsAbsent(com.fasterxml.jackson.databind.JsonNode node,List<String> privateValues) {
        if(node.isObject()) node.fields().forEachRemaining(field->{
            if(field.getKey().equals("data")&&field.getValue().isTextual()) {
                try {
                    String decoded=new String(Base64.getDecoder().decode(field.getValue().asText()),java.nio.charset.StandardCharsets.UTF_8);
                    for(String secret:privateValues)assertThat(decoded).doesNotContain(secret);
                } catch(IllegalArgumentException notBase64) { /* other JSON metadata */ }
            }
            assertPrivatePayloadsAbsent(field.getValue(),privateValues);
        });
        else if(node.isArray())node.forEach(value->assertPrivatePayloadsAbsent(value,privateValues));
    }

    @Test void transientActivityFailureRetriesAndCompletedModelReceiptsAreReused() throws Exception {
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        when(model.turn(anyList(),anyList())).thenAnswer(invocation->{
            if(attempts.getAndIncrement()==0)throw new IllegalStateException("transient local provider failure");
            return new ToolCallingPort.Response("No evidence",List.of(),"scripted-fixture:v1");
        });
        var run=as("investigator",()->investigations.launch(caseId,"Retry fixture","retry"));
        await(run.id(),"INSUFFICIENT_EVIDENCE");
        assertThat(attempts.get()).isEqualTo(2);
        as("investigator",()->{ assertThat(activities.advance(run.id().toString(),0)).isEqualTo("INSUFFICIENT_EVIDENCE");return null; });
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT active FROM orchestration.execution_reservations WHERE run_id=?",Boolean.class,run.id())).isFalse();
    }

    @Test void revokedDelegationAndRetryExhaustionReleaseReservations() throws Exception {
        factory.shutdownNow();factory.awaitTermination(5,TimeUnit.SECONDS);
        var denied=as("investigator",()->investigations.launch(caseId,"Revoked fixture","revoked"));
        jdbc.update("UPDATE orchestration.worker_delegations SET revoked=true WHERE run_id=?",denied.id());
        startWorker();
        await(denied.id(),"FAILED");
        verifyNoInteractions(model);
        when(model.turn(anyList(),anyList())).thenThrow(new IllegalStateException("provider remains unavailable"));
        var failed=as("investigator",()->investigations.launch(caseId,"Exhaust retries","exhausted"));
        await(failed.id(),"FAILED");
        verify(model,times(3)).turn(anyList(),anyList());
        assertThat(jdbc.queryForObject("SELECT active FROM orchestration.execution_reservations WHERE run_id=?",Boolean.class,failed.id())).isFalse();
    }

    @Test void duplicateLaunchAndCancelledWorkDoNotProduceAnEffect() throws Exception {
        factory.shutdownNow();factory.awaitTermination(5,TimeUnit.SECONDS);
        var first=as("investigator",()->investigations.launch(caseId,"Cancel fixture","cancel"));
        assertThat(as("investigator",()->investigations.launch(caseId,"Cancel fixture","cancel")).id()).isEqualTo(first.id());
        delivery.deliver();
        client.newUntypedWorkflowStub(first.id().toString()).cancel();
        startWorker();
        await(first.id(),"CANCELLED");
        assertThat(jdbc.queryForObject("SELECT active FROM orchestration.execution_reservations WHERE run_id=?",Boolean.class,first.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM decision.simulated_accounts WHERE case_id=?",Integer.class,caseId)).isZero();
    }

    @Test void proposalOperationKeyCannotBeReusedWithChangedInput() {
        var proof=evidence();
        var first=as("investigator",()->decisions.proposeOnce("operation",caseId,"First rationale",proof,"scripted-fixture:v1"));
        assertThat(as("investigator",()->decisions.proposeOnce("operation",caseId,"First rationale",proof,"scripted-fixture:v1")).id()).isEqualTo(first.id());
        assertThatThrownBy(()->as("investigator",()->decisions.proposeOnce("operation",caseId,"Changed rationale",proof,"scripted-fixture:v1")))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void durableActivityUsesThePinnedReleaseRatherThanTheCurrentDefault() throws Exception {
        var release=mapper.readValue(new org.springframework.core.io.ClassPathResource("releases/release-a.json").getInputStream(),com.fraudengine.platform.api.ReleaseEnvelope.class);
        when(releases.active()).thenReturn(release);
        when(model.turnPinned(anyList(),anyList(),eq(release.modelVersion()))).thenReturn(new ToolCallingPort.Response("Synthetic protocol fixture",List.of(),release.modelVersion()));
        var launched=as("investigator",()->investigations.launch(caseId,"Read bound case","release-pin-"+UUID.randomUUID()));
        await(launched.id(),"INSUFFICIENT_EVIDENCE");
        verify(model).turnPinned(anyList(),anyList(),eq(release.modelVersion()));verify(model,never()).turn(anyList(),anyList());
        assertThat(jdbc.queryForObject("SELECT release_envelope->>'releaseId' FROM orchestration.investigations WHERE id=?",String.class,launched.id())).isEqualTo(release.releaseId());
    }

    @Test void launchedPromptIsPinnedAndCannotBeMutated() {
        factory.shutdownNow(); factory.awaitTermination(5,TimeUnit.SECONDS);
        var launched=as("investigator",()->investigations.launch(caseId,"Read bound case","prompt-pin-"+UUID.randomUUID()));
        String original=jdbc.queryForObject("SELECT prompt_reference FROM orchestration.investigations WHERE id=?",String.class,launched.id());
        var catalog=new com.fraudengine.platform.gateway.PromptRegistry(mapper,"investigator-v2");
        assertThat(original).startsWith("investigator-v1@");
        assertThat(catalog.active().reference()).startsWith("investigator-v2@").isNotEqualTo(original);
        assertThatThrownBy(()->jdbc.update("UPDATE orchestration.investigations SET prompt_reference=? WHERE id=?",catalog.active().reference(),launched.id()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT prompt_reference FROM orchestration.investigations WHERE id=?",String.class,launched.id())).isEqualTo(original);
    }

}
