/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/provider/OllamaToolCallingAdapter.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Translates explicit tool schemas and message turns to the native local Ollama API.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - This adapter returns requested calls; it has no dispatcher or action executor reference.
 *   - Multiple-call responses and oversized output fail explicitly instead of executing an unbounded batch.
 *   - The existing loopback-only client applies timeouts and prohibits cloud redirects.
 * ============================================================================
 */
package com.fraudengine.platform.gateway.provider;

import com.fraudengine.platform.api.ToolCallingPort;
import com.fraudengine.platform.gateway.ModelUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class OllamaToolCallingAdapter implements ToolCallingPort {
    private final OllamaLocalClient client;
    private final String model;
    private final ObjectMapper mapper;

    public OllamaToolCallingAdapter(OllamaLocalClient client, String model, ObjectMapper mapper) {
        this.client = client; this.model = model; this.mapper = mapper;
    }

    @Override
    public Response turn(List<Message> messages, List<Definition> tools) {
        String version = "ollama:" + model + "@" + client.digest(model);
        var encoded = messages.stream().map(message -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("role", message.role()); value.put("content", message.content());
            if (message.call() != null) value.put("tool_calls", List.of(Map.of("type", "function", "function",
                    Map.of("name", message.call().name(), "arguments", message.call().arguments()))));
            if (message.toolName() != null) value.put("tool_name", message.toolName());
            return value;
        }).toList();
        var schemas = tools.stream().map(tool -> Map.of("type", "function", "function",
                Map.of("name", tool.name(), "description", tool.description(), "parameters", tool.schema()))).toList();
        var raw = client.post("/api/chat", Map.of("model", model, "stream", false,
                "messages", encoded, "tools", schemas, "options", Map.of("temperature", 0, "num_predict", 512)));
        var message = raw.path("message");
        if (!message.isObject()) throw new ModelUnavailableException("Malformed tool-calling response");
        String content = message.path("content").asText("");
        var calls = message.path("tool_calls");
        if (content.length() > 8000 || (!calls.isMissingNode() && (!calls.isArray() || calls.size() > 1))) {
            throw new ModelUnavailableException("Tool response exceeded the allowed shape");
        }
        List<Call> result = new ArrayList<>();
        for (var call : calls) {
            var function = call.path("function");
            var args = function.path("arguments");
            if (!function.path("name").isTextual() || function.path("name").asText().length() > 100 || !args.isObject() || args.toString().length() > 5000) {
                throw new ModelUnavailableException("Malformed tool arguments");
            }
            result.add(new Call(UUID.randomUUID().toString(), function.path("name").asText(),
                    mapper.convertValue(args, new TypeReference<Map<String, Object>>() {})));
        }
        if (!version.equals("ollama:" + model + "@" + client.digest(model))) {
            throw new ModelUnavailableException("Model changed during tool turn");
        }
        return new Response(content, List.copyOf(result), version);
    }
}
