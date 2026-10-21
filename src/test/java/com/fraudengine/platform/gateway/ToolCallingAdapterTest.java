/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/gateway/ToolCallingAdapterTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Verify native Ollama tool request/result turns against a controlled loopback server.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - This is wire-contract verification, not a real semantic-model quality result.
 *   - The provider adapter cannot execute a callback or action itself.
 * ============================================================================
 */
package com.fraudengine.platform.gateway;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.platform.api.ToolCallingPort;
import com.fraudengine.platform.gateway.provider.OllamaLocalClient;
import com.fraudengine.platform.gateway.provider.OllamaToolCallingAdapter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;

class ToolCallingAdapterTest {
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private String response;
    private final java.util.concurrent.atomic.AtomicReference<String> received = new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] data = "{\"models\":[{\"name\":\"tool-model:latest\",\"digest\":\"test-digest\"}]}".getBytes();
            exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close();
        });
        server.createContext("/api/chat", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] data = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close();
        });
        server.start();
    }

    @AfterEach void close() { server.stop(0); }

    OllamaToolCallingAdapter adapter() {
        return new OllamaToolCallingAdapter(new OllamaLocalClient("http://127.0.0.1:" + server.getAddress().getPort(), mapper), "tool-model", mapper);
    }

    @Test
    void returnsNativeToolCallAndSendsResultsAsToolData() throws Exception {
        response = "{\"message\":{\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"get_case_details\",\"arguments\":{}}}]}}";
        var tools = List.of(new ToolCallingPort.Definition("get_case_details", "read", Map.of("type", "object")));
        var first = adapter().turn(List.of(new ToolCallingPort.Message("user", "Read case", null, null)), tools);
        assertThat(first.calls()).hasSize(1);
        assertThat(first.calls().getFirst().id()).isNotBlank();
        assertThat(first.modelVersion()).isEqualTo("ollama:tool-model@test-digest");
        response = "{\"message\":{\"content\":\"Evidence is insufficient\"}}";
        adapter().turn(List.of(new ToolCallingPort.Message("assistant", "", first.calls().getFirst(), null),
                new ToolCallingPort.Message("tool", "untrusted tool data", null, "get_case_details")), tools);
        var messages = mapper.readTree(received.get()).path("messages");
        assertThat(messages.get(0).path("tool_calls").get(0).path("function").path("name").asText()).isEqualTo("get_case_details");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("tool");
        assertThat(messages.get(1).path("content").asText()).isEqualTo("untrusted tool data");
    }

    @Test
    void rejectsParallelToolBatchAndMalformedArguments() {
        for (String calls : List.of("[{\"function\":{}},{\"function\":{}}]",
                "[{\"function\":{\"name\":\"get_case_details\",\"arguments\":\"not-an-object\"}}]")) {
            response = "{\"message\":{\"content\":\"\",\"tool_calls\":" + calls + "}}";
            assertThatThrownBy(() -> adapter().turn(List.of(), List.of())).isInstanceOf(ModelUnavailableException.class);
        }
    }
}
