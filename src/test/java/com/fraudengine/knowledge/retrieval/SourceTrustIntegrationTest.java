/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/knowledge/retrieval/SourceTrustIntegrationTest.java
 * CONTEXT:     knowledge
 * LAYER:       test
 * PURPOSE:     Prove curator trust, exact revision checks and immediate revocation before provider inference.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The PostgreSQL and HTTP security chain are real; embedding computations are labelled fixtures.
 *   - A spy verifies that untrusted-source requests never invoke embedding.
 * ============================================================================
 */
package com.fraudengine.knowledge.retrieval;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fraudengine.platform.gateway.RetrievalEmbeddingPort;
import com.fraudengine.platform.security.AuthenticatedHttp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"knowledge.trust.required=true","knowledge.retrieval.embedding-provider=fixture"})
@ActiveProfiles("test") @Testcontainers
class SourceTrustIntegrationTest {
    @Container static final PostgreSQLContainer<?> DATABASE=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",DATABASE::getJdbcUrl);properties.add("spring.datasource.username",DATABASE::getUsername);properties.add("spring.datasource.password",DATABASE::getPassword);AuthenticatedHttp.accounts(properties);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @MockitoSpyBean RetrievalEmbeddingPort embeddings;
    @Test void untrustedRevisionCannotReachEmbeddingAndRevocationImmediatelyDeniesReads() {
        UUID id=UUID.randomUUID();String text="Synthetic policy requires human approval for an account freeze.";
        String hash=com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(text);
        jdbc.update("INSERT INTO knowledge.document_registry(id,source_id,source_version,doc_type,classification,checksum,content_hash,chunk_count,ingested_by,raw_uri) VALUES (?,'trust-policy',1,'policy','C0',?,?,1,'test','test:fixture')",id,hash,hash);
        jdbc.update("INSERT INTO knowledge.vector_store(id,content,metadata) VALUES (?,?,'{}'::jsonb)",id,text);
        jdbc.update("INSERT INTO knowledge.chunks_lineage(id,document_id,chunk_index,chunk_hash,vector_id) VALUES (?,?,0,?,?)",id,id,hash,id);
        jdbc.update("INSERT INTO knowledge.corpus_versions(run_id,document_count,chunk_count) VALUES (1,1,1)");jdbc.update("INSERT INTO knowledge.corpus_members VALUES (1,?)",id);
        var curator=new AuthenticatedHttp(http);var investigator=new AuthenticatedHttp(http,"investigator");
        var trust=Map.of("sourceId","trust-policy","sourceVersion",1,"checksum",hash,"revoked",false);
        assertThat(curator.postForEntity("/v1/knowledge/retrieval/index",Map.of("corpusVersion",1),String.class).getStatusCode().value()).isEqualTo(403);
        verify(embeddings,never()).embed(anyString());
        assertThat(investigator.postForEntity("/v1/knowledge/source-trust",trust,String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(curator.postForEntity("/v1/knowledge/source-trust",Map.of("sourceId","trust-policy","sourceVersion",1,"checksum","0".repeat(64),"revoked",false),String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(curator.postForEntity("/v1/knowledge/source-trust",trust,String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(curator.postForEntity("/v1/knowledge/retrieval/index",Map.of("corpusVersion",1),String.class).getStatusCode().value()).isEqualTo(200);
        verify(embeddings,atLeastOnce()).embed(anyString());
        assertThat(curator.postForEntity("/v1/knowledge/source-trust",Map.of("sourceId","trust-policy","sourceVersion",1,"checksum",hash,"revoked",true),String.class).getStatusCode().value()).isEqualTo(200);
        clearInvocations(embeddings);
        assertThat(curator.postForEntity("/v1/knowledge/answers",Map.of("query","human approval freeze","corpusVersion",1),String.class).getStatusCode().value()).isEqualTo(403);
        verify(embeddings,never()).embed(anyString());
    }
    @org.junit.jupiter.api.AfterAll static void closeTelemetryBeforeDatabaseStops() {
        new org.springframework.test.context.TestContextManager(SourceTrustIntegrationTest.class).getTestContext()
            .markApplicationContextDirty(org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);
    }

}
