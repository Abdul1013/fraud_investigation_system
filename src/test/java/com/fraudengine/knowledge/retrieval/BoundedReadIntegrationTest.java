/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/knowledge/retrieval/BoundedReadIntegrationTest.java
 * CONTEXT:     knowledge
 * LAYER:       test
 * PURPOSE:     Exercise cache isolation, revocation, throttling and degraded reads against PostgreSQL and HTTP.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Synthetic content and fixture embeddings verify infrastructure; native workload evidence is recorded separately.
 *   - Each test resets rate state and cache identities through database scope revisions.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.platform.gateway.*;
import com.fraudengine.platform.security.AuthenticatedHttp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "knowledge.trust.required=true","knowledge.retrieval.embedding-provider=fixture","knowledge.retrieval.answer-provider=extractive",
 "platform.admission.maximum-concurrent-reads=1","platform.rate-limit.reads-per-minute=100","knowledge.cache.maximum-entries=2"})
@ActiveProfiles("test") @Testcontainers
class BoundedReadIntegrationTest {
 @Container static final PostgreSQLContainer<?> DATABASE=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",DATABASE::getJdbcUrl);p.add("spring.datasource.username",DATABASE::getUsername);p.add("spring.datasource.password",DATABASE::getPassword);AuthenticatedHttp.accounts(p);}
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;@Autowired RetrievalIndexService index;@Autowired AuthorizedEvidenceCache cache;
 @MockitoSpyBean RetrievalEmbeddingPort embeddings;
 String text="Transaction receipts and account statements are required as evidence for human review.";
 RetrievalRequest query(String text){return new RetrievalRequest(text,1,null,5,null,null);}
 AuthenticatedHttp investigator(){return new AuthenticatedHttp(http,"investigator");}
 @BeforeEach void seed(){
  reset(embeddings);cache.clear();
  jdbc.execute("TRUNCATE platform.rate_buckets,case_management.case_sources,case_management.case_grants,case_management.case_records");
  jdbc.execute("TRUNCATE knowledge.ingestion_runs,knowledge.ingestion_failures,knowledge.ingestion_sources,knowledge.trusted_sources,knowledge.retrieval_embeddings,knowledge.retrieval_spaces,knowledge.corpus_members,knowledge.corpus_versions,knowledge.chunks_lineage,knowledge.document_registry,knowledge.vector_store RESTART IDENTITY");
  UUID id=UUID.randomUUID();String hash=com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(text);
  jdbc.update("INSERT INTO case_management.case_records(case_id,status,summary,classification) VALUES ('bounded-case','INVESTIGATING','Synthetic','C0')");
  for(String actor:List.of("investigator","analyst"))jdbc.update("INSERT INTO case_management.case_grants VALUES ('bounded-case',?,'C2')",actor);
  jdbc.update("INSERT INTO case_management.case_sources VALUES ('bounded-case','bounded-policy')");
  jdbc.update("INSERT INTO knowledge.document_registry(id,source_id,source_version,doc_type,classification,checksum,content_hash,chunk_count,ingested_by,raw_uri) VALUES (?,'bounded-policy',1,'policy','C0',?,?,1,'test','test:fixture')",id,hash,hash);
  jdbc.update("INSERT INTO knowledge.vector_store(id,content,metadata) VALUES (?,?,'{}'::jsonb)",id,text);
  jdbc.update("INSERT INTO knowledge.chunks_lineage(id,document_id,chunk_index,chunk_hash,vector_id) VALUES (?,?,0,?,?)",id,id,hash,id);
  jdbc.update("INSERT INTO knowledge.corpus_versions(run_id,document_count,chunk_count) VALUES (1,1,1)");jdbc.update("INSERT INTO knowledge.corpus_members VALUES (1,?)",id);
  jdbc.update("INSERT INTO knowledge.trusted_sources(source_id,source_version,checksum,trusted_by) VALUES ('bounded-policy',1,?,'test')",hash);
  index.index(1);clearInvocations(embeddings);
 }
 @Test void cacheSeparatesSubjectsAndRevokesGrantsImmediately(){
  var q=query("What transaction receipts evidence is required?");
  assertThat(investigator().postForEntity("/v1/cases/bounded-case/evidence",q,RetrievalResult.class).getBody().passages()).isNotEmpty();
  assertThat(investigator().postForEntity("/v1/cases/bounded-case/evidence",q,RetrievalResult.class).getStatusCode().value()).isEqualTo(200);
  verify(embeddings,times(1)).embed(q.query());
  assertThat(new AuthenticatedHttp(http,"analyst").postForEntity("/v1/cases/bounded-case/evidence",q,String.class).getStatusCode().value()).isEqualTo(200);
  verify(embeddings,times(2)).embed(q.query());
  jdbc.update("DELETE FROM case_management.case_grants WHERE case_id='bounded-case' AND subject='investigator'");
  assertThat(investigator().postForEntity("/v1/cases/bounded-case/evidence",q,String.class).getStatusCode().value()).isEqualTo(403);
 }
 @Test void sameSubjectCannotReuseCacheAcrossCases(){
  var q=query("What transaction receipt evidence is required?");
  investigator().postForEntity("/v1/cases/bounded-case/evidence",q,String.class);
  jdbc.update("INSERT INTO case_management.case_records(case_id,status,summary,classification) VALUES ('other-case','INVESTIGATING','Synthetic','C0')");
  jdbc.update("INSERT INTO case_management.case_grants VALUES ('other-case','investigator','C2')");
  jdbc.update("INSERT INTO case_management.case_sources VALUES ('other-case','bounded-policy')");
  assertThat(investigator().postForEntity("/v1/cases/other-case/evidence",q,String.class).getStatusCode().value()).isEqualTo(200);
  verify(embeddings,times(2)).embed(q.query());
 }
 @Test void cacheNamespaceChangesAutomaticallyAndTrustRevocationRejectsCachedEvidence(){
  var q=query("What transaction receipts evidence is required?");
  investigator().postForEntity("/v1/cases/bounded-case/evidence",q,RetrievalResult.class);
  long before=jdbc.queryForObject("SELECT revision FROM case_management.scope_revisions WHERE case_id='bounded-case'",Long.class);
  jdbc.update("UPDATE case_management.case_grants SET classification_ceiling='C1' WHERE case_id='bounded-case' AND subject='investigator'");
  assertThat(jdbc.queryForObject("SELECT revision FROM case_management.scope_revisions WHERE case_id='bounded-case'",Long.class)).isGreaterThan(before);
  investigator().postForEntity("/v1/cases/bounded-case/evidence",q,RetrievalResult.class);verify(embeddings,times(2)).embed(q.query());
  jdbc.update("UPDATE knowledge.trusted_sources SET revoked=true WHERE source_id='bounded-policy'");
  assertThat(investigator().postForEntity("/v1/cases/bounded-case/evidence",q,String.class).getStatusCode().value()).isEqualTo(403);
 }
 @Test void credentialQueriesBypassCacheAndEntryBudgetIsBounded(){
  var q=query("What password evidence receipts are required?");
  for(int i=0;i<2;i++)investigator().postForEntity("/v1/cases/bounded-case/evidence",q,String.class);
  verify(embeddings,times(2)).embed(q.query());
  for(int i=0;i<5;i++)investigator().postForEntity("/v1/cases/bounded-case/evidence",query("What transaction receipt evidence is required "+i+"?"),String.class);
  assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
 }
 @Test void failedModelUsesAuthorizedLexicalEvidenceWithoutFixtureFallback(){
  doThrow(new ModelUnavailableException("Synthetic outage")).when(embeddings).spaceId();
  var result=investigator().postForEntity("/v1/cases/bounded-case/answers",query("transaction receipts evidence"),AnswerResult.class);
  assertThat(result.getStatusCode().value()).isEqualTo(200);assertThat(result.getBody().status()).isEqualTo("DEGRADED_READ");
  assertThat(result.getBody().retrieval().degraded()).isTrue();assertThat(result.getBody().retrieval().fixtureEmbeddings()).isFalse();
  assertThat(result.getBody().evidence()).allSatisfy(q->assertThat(text).contains(q.quote()));verify(embeddings,never()).embed(anyString());
  jdbc.update("DELETE FROM case_management.case_grants WHERE subject='investigator'");
  assertThat(investigator().postForEntity("/v1/cases/bounded-case/evidence",query("transaction receipts"),String.class).getStatusCode().value()).isEqualTo(403);
 }
 @Test void databaseRateBudgetReturnsExplicitRetryAfter(){
  jdbc.update("INSERT INTO platform.rate_buckets VALUES ('investigator','rag-read',date_trunc('minute',now()),100)");
  var result=investigator().postForEntity("/v1/cases/bounded-case/evidence",query("transaction receipts"),String.class);
  assertThat(result.getStatusCode().value()).isEqualTo(429);assertThat(Integer.parseInt(result.getHeaders().getFirst("Retry-After"))).isBetween(1,60);
 }
 @Test void concurrentReadIsRejectedWithoutWaitingForProvider()throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  doAnswer(call->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Fixture timeout");return call.callRealMethod();}).when(embeddings).embed(anyString());
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
   var first=pool.submit(()->investigator().postForEntity("/v1/cases/bounded-case/evidence",query("transaction receipt first"),String.class));
   assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
   try{assertThat(investigator().postForEntity("/v1/cases/bounded-case/evidence",query("transaction receipt second"),String.class).getStatusCode().value()).isEqualTo(429);}finally{release.countDown();}
   assertThat(first.get(10,TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
  }
 }
 @Test void progressStreamStopsAfterGrantRevocationAndDoesNotExposeEventDetails()throws Exception{
  var id=UUID.randomUUID();
  jdbc.update("INSERT INTO orchestration.investigations(id,case_id,subject,request_key,goal,state) VALUES (?,'bounded-case','investigator',?,'Synthetic','PENDING_APPROVAL')",id,id.toString());
  jdbc.update("INSERT INTO orchestration.investigation_events VALUES (?,1,'PENDING_APPROVAL','RESTRICTED_MODEL_DETAIL_SENTINEL',now())",id);
  var client=java.net.http.HttpClient.newHttpClient();
  var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v1/investigations/"+id+"/stream"))
   .header("Authorization","Basic "+Base64.getEncoder().encodeToString(("investigator:"+AuthenticatedHttp.PASSWORD).getBytes(java.nio.charset.StandardCharsets.UTF_8))).GET().build();
  var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofInputStream());assertThat(response.statusCode()).isEqualTo(200);
  try(var input=response.body();var pool=Executors.newVirtualThreadPerTaskExecutor()){
   var lines=new java.io.BufferedReader(new java.io.InputStreamReader(input));var collected=new StringBuilder();
   for(String line;(line=lines.readLine())!=null;){collected.append(line).append('\n');if(line.contains("event:state"))break;}
   jdbc.update("DELETE FROM case_management.case_grants WHERE subject='investigator'");
   var tail=pool.submit(()->{String line;while((line=lines.readLine())!=null)collected.append(line).append('\n');return collected.toString();});
   assertThat(tail.get(8,TimeUnit.SECONDS)).contains("access_revoked").doesNotContain("RESTRICTED_MODEL_DETAIL_SENTINEL");
  }
 }
 @AfterAll static void close(){new TestContextManager(BoundedReadIntegrationTest.class).getTestContext().markApplicationContextDirty(org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);}
}
