/**
 * WEEK 27 — Provider wire tests use a local stub server to verify HTTP shapes, model digest lookup,
 * structured output parsing, and redirect rejection. A stub response is not real-model quality
 * evidence; this suite protects the integration contract while the semantic comparison uses an
 * actual installed model.
 */
package com.fraudengine.platform.gateway;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.platform.gateway.provider.*;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.util.*;

class OllamaAdaptersTest {
    HttpServer server;
    OllamaLocalClient client;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/tags",
                exchange -> {
                    byte[] json =
                            "{\"models\":[{\"name\":\"test-model:latest\",\"digest\":\"sha256-test\"}]}"
                                    .getBytes();
                    exchange.sendResponseHeaders(200, json.length);
                    exchange.getResponseBody().write(json);
                    exchange.close();
                });
        server.start();
        client = new OllamaLocalClient("http://127.0.0.1:" + server.getAddress().getPort(), mapper);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void embeddingWireContractPinsInstalledDigestAndDisablesTruncation() {
        server.createContext(
                "/api/embed",
                exchange -> {
                    var request = mapper.readTree(exchange.getRequestBody());
                    assertThat(request.path("truncate").asBoolean()).isFalse();
                    assertThat(request.path("model").asText()).isEqualTo("test-model");
                    byte[] json = "{\"embeddings\":[[0.5,0.25]]}".getBytes();
                    exchange.sendResponseHeaders(200, json.length);
                    exchange.getResponseBody().write(json);
                    exchange.close();
                });
        var model = new OllamaRetrievalEmbedding(client, "test-model");
        assertThat(model.spaceId()).isEqualTo("ollama:test-model@sha256-test");
        assertThat(model.embed("public evidence")).containsExactly(0.5f, 0.25f);
    }

    @Test
    void structuredSelectionUsesTheSameNarrowContractAsOfflineAnswers() {
        server.createContext(
                "/api/chat",
                exchange -> {
                    var request = mapper.readTree(exchange.getRequestBody());
                    assertThat(request.path("stream").asBoolean()).isFalse();
                    assertThat(request.path("format").path("type").asText()).isEqualTo("object");
                    String content =
                            "{\"claims\":[{\"chunkId\":\"chunk-1\",\"quote\":\"Exact evidence"
                                    + " quote.\"}]}";
                    byte[] response =
                            mapper.writeValueAsBytes(Map.of("message", Map.of("content", content)));
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        var answers = new OllamaEvidenceAnswer(client, "test-model", mapper);
        assertThat(
                        answers.select(
                                "Question",
                                List.of(
                                        new EvidenceAnswerPort.Evidence(
                                                "chunk-1", "Exact evidence quote."))))
                .containsExactly(
                        new EvidenceAnswerPort.ProposedQuote("chunk-1", "Exact evidence quote."));
    }

    @Test
    void externalEndpointsAndRedirectsCannotReceiveEvidence() {
        assertThatThrownBy(() -> new OllamaLocalClient("https://example.com", mapper))
                .isInstanceOf(IllegalArgumentException.class);
        server.createContext(
                "/api/embed",
                exchange -> {
                    exchange.getResponseHeaders().add("Location", "https://example.com");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        assertThatThrownBy(
                        () ->
                                new OllamaRetrievalEmbedding(client, "test-model")
                                        .embed("confidential evidence"))
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("302");
    }
}
