/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/security/KeycloakStepUpIntegrationTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Exercise real browser PKCE and password/OTP authentication before human-only simulator approval.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Keycloak credentials and OTP seed are generated in test memory and copied only to an ephemeral container.
 *   - Proposal evidence uses an explicitly labelled fixture; approval uses actual verified Keycloak access JWTs.
 *   - No bearer token, password, OTP seed or approval credential is written to evidence.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fraudengine.platform.api.*;
import com.fraudengine.decision.api.DecisionApi;
import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "engine.security.mode=oidc","investigation.engine=local","knowledge.retrieval.embedding-provider=fixture"})
@ActiveProfiles("test")
@Testcontainers
class KeycloakStepUpIntegrationTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String PASSWORD=UUID.randomUUID()+"aA7!";
    static final String EXTERNAL_SUBJECT=UUID.randomUUID().toString();
    static final byte[] OTP_SEED=new byte[20];
    static { new SecureRandom().nextBytes(OTP_SEED); }
    static final String CALLBACK="http://localhost:8080/auth/index.html";
    static String realm() {
        try {
            ObjectNode realm=(ObjectNode)JSON.readTree(Files.readString(Path.of("infra/keycloak/fraud-realm.json")));
            realm.remove(List.of("_file","_purpose"));
            // Ephemeral loopback protocol fixture: Docker bridge addresses can otherwise trigger external-SSL policy.
            // This test proves password/OTP and signed access validation, not deployment TLS.
            realm.put("sslRequired","none");
            var other=((ObjectNode)realm.path("clients").get(0)).deepCopy();
            other.put("clientId","fraud-other");
            ((ObjectNode)other.path("protocolMappers").get(0).path("config")).put("included.custom.audience","other-api");
            ((ArrayNode)realm.path("clients")).add(other);
            var user=realm.putArray("users").addObject();
            user.put("id",EXTERNAL_SUBJECT).put("username","untrusted-display-name").put("enabled",true)
                    .put("firstName","Synthetic").put("lastName","Analyst").put("email","analyst@example.invalid").put("emailVerified",true);
            var credentials=user.putArray("credentials");
            credentials.addObject().put("type","password").put("value",PASSWORD).put("temporary",false);
            credentials.addObject().put("type","otp")
                .put("secretData",JSON.writeValueAsString(Map.of("value",base32(OTP_SEED))))
                .put("credentialData",JSON.writeValueAsString(Map.of("subType","totp","digits",6,"counter",0,"period",30,"algorithm","HmacSHA1","secretEncoding","BASE32")));
            return JSON.writeValueAsString(realm);
        } catch(Exception error) { throw new IllegalStateException("Cannot build ephemeral identity fixture",error); }
    }
    @Container static final PostgreSQLContainer<?> DATABASE=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Container static final GenericContainer<?> KEYCLOAK=new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.8.1"))
        .withCopyToContainer(Transferable.of(realm()),"/opt/keycloak/data/import/fraud-realm.json")
        .withCommand("start-dev","--import-realm").withExposedPorts(8080)
        .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/realms/fraud/.well-known/openid-configuration").forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(3));
    static String issuer() { return "http://localhost:"+KEYCLOAK.getMappedPort(8080)+"/realms/fraud"; }
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username",DATABASE::getUsername);
        properties.add("spring.datasource.password",DATABASE::getPassword);
        properties.add("engine.security.issuer",KeycloakStepUpIntegrationTest::issuer);
        properties.add("decision.approval-key",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DecisionApi decisions;
    @Autowired KnowledgeApi knowledge;
    @Autowired com.fraudengine.knowledge.retrieval.RetrievalIndexService fixtureIndex;
    @Autowired OidcIdentityLinks links;
    final HttpClient api=HttpClient.newHttpClient();
    String caseId;
    @BeforeEach void seed() {
        jdbc.update("INSERT INTO platform.identity_links VALUES (?,?,'analyst','ANALYST',true) ON CONFLICT DO NOTHING",issuer(),EXTERNAL_SUBJECT);
        caseId="otp-"+UUID.randomUUID();
        jdbc.update("INSERT INTO case_management.case_records(case_id,status,summary,classification) VALUES (?,'INVESTIGATING','Synthetic OTP approval case','C0')",caseId);
        for(String subject:List.of("analyst","investigator"))jdbc.update("INSERT INTO case_management.case_grants VALUES (?,?,'C2')",caseId,subject);
        jdbc.update("INSERT INTO case_management.case_sources VALUES (?,'otp-policy')",caseId);
    }
    <T>T fixtureAs(String subject,Set<Permission> permissions,Supplier<T> work) {
        var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(subject,null,permissions.stream().map(p->new SimpleGrantedAuthority(p.name())).toList()));
        try { SecurityContextHolder.setContext(context);return work.get(); }finally {SecurityContextHolder.setContext(previous);}
    }
    UUID fixtureProposal() {
        UUID id=UUID.randomUUID();String text="Transaction receipts show inconsistent evidence requiring human review.";
        String hash=HexFormat.of().formatHex(sha(text));
        jdbc.update("INSERT INTO knowledge.document_registry(id,source_id,source_version,doc_type,classification,checksum,content_hash,chunk_count,ingested_by,raw_uri) VALUES (?,'otp-policy',1,'policy','C0',?,?,1,'test','test:fixture')",id,hash,hash);
        jdbc.update("INSERT INTO knowledge.vector_store(id,content,metadata) VALUES (?,?,'{}'::jsonb)",id,text);
        jdbc.update("INSERT INTO knowledge.chunks_lineage(id,document_id,chunk_index,chunk_hash,vector_id) VALUES (?,?,0,?,?)",id,id,hash,id);
        jdbc.update("INSERT INTO knowledge.corpus_versions(run_id,document_count,chunk_count) VALUES (1,1,1)");
        jdbc.update("INSERT INTO knowledge.corpus_members VALUES (1,?)",id);
        fixtureAs("curator",SecurityAccounts.Role.KNOWLEDGE_CURATOR.permissions(),()->fixtureIndex.index(1));
        // Bypass no auth boundary: the proposal API requires an explicitly provisioned fixture investigator.
        return fixtureAs("investigator",SecurityAccounts.Role.INVESTIGATOR.permissions(),()->{
            var evidence=knowledge.retrieveCaseEvidence(caseId,new RetrievalRequest("transaction receipts evidence",1,null,5,null,null));
            return decisions.propose(caseId,"Human review of synthetic receipts",evidence,"scripted-fixture:v1").id();
        });
    }
    HttpResponse<String> request(String method,String path,String token,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(15));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(body==null)builder.method(method,HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
        return api.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void realPasswordOtpPkceAndLinkedIdentityGateSimulatorApproval() throws Exception {
        String passwordOnly=new Browser().login("1",false);
        var me=request("GET","/v1/security/me",passwordOnly,null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(me.body()).path("subject").asText()).isEqualTo("analyst");
        assertThat(request("GET","/v1/security/me",null,null).statusCode()).isEqualTo(401);
        assertThat(request("GET","/v1/security/me",passwordOnly+"changed",null).statusCode()).isEqualTo(401);
        String wrongIssuer=new Browser(issuer().replace("localhost","127.0.0.1"),"fraud-learning").login("1",false);
        assertThat(request("GET","/v1/security/me",wrongIssuer,null).statusCode()).isEqualTo(401);
        String wrongAudience=new Browser(issuer(),"fraud-other").login("1",false);
        assertThat(request("GET","/v1/security/me",wrongAudience,null).statusCode()).isEqualTo(401);
        UUID proposal=fixtureProposal();
        assertThat(request("POST","/v1/recommendations/"+proposal+"/approve",passwordOnly,null).statusCode()).isEqualTo(403);
        var browser=new Browser();String stepped=browser.login("2",true);
        assertThat(browser.otpChallenge).isTrue();assertThat(browser.wrongOtpRejected).isTrue();
        var approved=request("POST","/v1/recommendations/"+proposal+"/approve",stepped,null);
        assertThat(approved.statusCode()).isEqualTo(200);
        String approval=JSON.readTree(approved.body()).path("token").asText();assertThat(approval).isNotBlank();
        var executed=request("POST","/v1/recommendations/"+proposal+"/execute",stepped,JSON.writeValueAsString(Map.of("token",approval,"idempotencyKey","otp-once")));
        assertThat(executed.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT freeze_count FROM decision.simulated_accounts WHERE case_id=?",Integer.class,caseId)).isEqualTo(1);
        jdbc.update("UPDATE platform.identity_links SET enabled=false WHERE issuer=? AND external_subject=?",issuer(),EXTERNAL_SUBJECT);
        assertThat(request("GET","/v1/security/me",stepped,null).statusCode()).isEqualTo(401);
        var evidence=new LinkedHashMap<String,Object>(Map.of("_file","docs/evidence/week31/otp-authentication.json","_purpose","Real ephemeral Keycloak password/OTP and PKCE evidence; credentials omitted",
            "keycloak","26.8.1","passwordOnlyApprovalStatus",403,"otpChallenge",browser.otpChallenge,"wrongOtpRejected",browser.wrongOtpRejected,
            "verifiedApprovalStatus",approved.statusCode(),"simulatedFreezeCount",1,"disabledIdentityStatus",401));
        evidence.put("wrongIssuerStatus",401);evidence.put("wrongAudienceStatus",401);
        Path output=Path.of("docs/evidence/week31/otp-authentication.json");Files.createDirectories(output.getParent());JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),evidence);
    }
    static String form(String html) {
        var matcher=java.util.regex.Pattern.compile("<form[^>]+action=\"([^\"]+)\"").matcher(html);
        if(!matcher.find())throw new AssertionError("Expected authentication form; raw response omitted");
        return matcher.group(1).replace("&amp;","&");
    }
    static class Browser {
        final String realmIssuer,clientId;
        Browser() { this(issuer(),"fraud-learning"); }
        Browser(String realmIssuer,String clientId) {this.realmIssuer=realmIssuer;this.clientId=clientId;}
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        boolean otpChallenge,wrongOtpRejected;
        String cookieMetadata="";
        HttpResponse<String> send(String url,Map<String,String> fields) throws Exception {
            var uri=URI.create(url);
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
            // Browsers allow Secure cookies on localhost; JDK CookieManager does not implement that exception.
            // Mirror it only for this exact loopback realm origin; staging still requires real TLS.
            if("http".equals(uri.getScheme())&&URI.create(realmIssuer).getHost().equals(uri.getHost())&&uri.getPort()==KEYCLOAK.getMappedPort(8080)) {
                String localCookies=cookies.getCookieStore().getCookies().stream()
                    .filter(cookie->!cookie.hasExpired()&&uri.getPath().startsWith(cookie.getPath()))
                    .map(cookie->cookie.getName()+"="+cookie.getValue()).collect(java.util.stream.Collectors.joining("; "));
                if(!localCookies.isEmpty())request.header("Cookie",localCookies);
            }
            if(fields==null)request.GET();else request.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(encoded(fields)));
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
            for(String header:response.headers().allValues("Set-Cookie"))
                for(var cookie:HttpCookie.parse(header))cookies.getCookieStore().add(uri,cookie);
            cookies.getCookieStore().getCookies().forEach(cookie->cookie.setVersion(0));
            cookieMetadata=cookies.getCookieStore().getCookies().stream().map(cookie->cookie.getName()+":secure="+cookie.getSecure()+":domain="+cookie.getDomain()+":path="+cookie.getPath()).toList().toString();
            return response;
        }
        String login(String level,boolean rejectWrongOtp) throws Exception {
            String verifier=UUID.randomUUID().toString()+UUID.randomUUID();
            String state=UUID.randomUUID().toString();
            String url=realmIssuer+"/protocol/openid-connect/auth?"+encoded(Map.of("client_id",clientId,"redirect_uri",CALLBACK,"scope","openid","response_type","code","state",state,
                    "code_challenge",Base64.getUrlEncoder().withoutPadding().encodeToString(sha(verifier)),"code_challenge_method","S256","acr_values",level,"max_age","0"));
            var response=send(url,null);
            for(int redirect=0;response.statusCode()==302&&redirect<5;redirect++)response=send(response.headers().firstValue("Location").orElseThrow(),null);
            assertThat(response.statusCode()).isEqualTo(200);
            response=send(form(response.body()),Map.of("username","untrusted-display-name","password",PASSWORD));
            if("2".equals(level)) {
                otpChallenge=response.statusCode()==200&&response.body().contains("name=\"otp\"");
                assertThat(otpChallenge).isTrue();
                if(rejectWrongOtp) {
                    response=send(form(response.body()),Map.of("otp","wrong-code"));
                    wrongOtpRejected=response.statusCode()==200&&response.body().contains("name=\"otp\"");
                    assertThat(wrongOtpRejected).isTrue();
                }
                response=send(form(response.body()),Map.of("otp",totp()));
            }
            if(response.statusCode()!=302) {
                String diagnostic=KEYCLOAK.getLogs().lines().filter(line->line.contains("error="))
                    .map(line->{var match=java.util.regex.Pattern.compile("error=\"([a-z_]+)\"").matcher(line);return match.find()?match.group(1):"authentication_error";})
                    .collect(java.util.stream.Collectors.joining(","));
                throw new AssertionError("Authentication status "+response.statusCode()+"; cookies="+cookieMetadata+"; "+diagnostic.replaceAll("sessionId=\"[^\"]+\"","sessionId=<redacted>"));
            }
            var query=query(URI.create(response.headers().firstValue("Location").orElseThrow()).getRawQuery());
            assertThat(query.get("state")).isEqualTo(state);assertThat(query).containsKey("code");
            var tokens=send(realmIssuer+"/protocol/openid-connect/token",Map.of("grant_type","authorization_code","client_id",clientId,"redirect_uri",CALLBACK,"code",query.get("code"),"code_verifier",verifier));
            assertThat(tokens.statusCode()).isEqualTo(200);
            return JSON.readTree(tokens.body()).path("access_token").asText();
        }
    }
    static Map<String,String> query(String value) {
        Map<String,String> result=new HashMap<>();for(String pair:value.split("&")){String[] parts=pair.split("=",2);result.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}return result;
    }
    static String encoded(Map<String,String> fields) { return fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&")); }
    static byte[] sha(String text) { try{return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));}catch(Exception error){throw new IllegalStateException(error);} }
    static String base32(byte[] bytes) { String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";StringBuilder output=new StringBuilder();int buffer=0,bits=0;for(byte value:bytes){buffer=(buffer<<8)|(value&255);bits+=8;while(bits>=5){bits-=5;output.append(alphabet.charAt((buffer>>bits)&31));}}if(bits>0)output.append(alphabet.charAt((buffer<<(5-bits))&31));return output.toString(); }
    static String totp() throws Exception {var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(OTP_SEED,"HmacSHA1"));byte[] hash=mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(Instant.now().getEpochSecond()/30).array());int offset=hash[hash.length-1]&15;int value=java.nio.ByteBuffer.wrap(hash,offset,4).getInt()&0x7fffffff;return "%06d".formatted(value%1_000_000);}
    @org.junit.jupiter.api.AfterAll static void closeTelemetryBeforeDatabaseStops() {
        new org.springframework.test.context.TestContextManager(KeycloakStepUpIntegrationTest.class).getTestContext()
            .markApplicationContextDirty(org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);
    }

}
