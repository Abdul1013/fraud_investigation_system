/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/provider/OllamaLocalClient.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Send bounded loopback-only provider requests after the centralized outbound privacy check.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-27
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - All POST bodies pass through RedactionFilter before serialization or HTTP dispatch.
 *   - Redirects and non-loopback endpoints are rejected; no cloud fallback is available.
 *   - Pattern redaction supplements structural classification and does not claim complete PII detection.
 * ============================================================================
 */
package com.fraudengine.platform.gateway.provider;

import com.fasterxml.jackson.databind.*;
import com.fraudengine.platform.gateway.ModelUnavailableException;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class OllamaLocalClient {
    private final URI base;
    private final com.fraudengine.platform.api.OperationalTelemetry telemetry;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public OllamaLocalClient(String address, ObjectMapper mapper) {
        this(address, mapper, null);
    }

    public OllamaLocalClient(String address, ObjectMapper mapper,
            com.fraudengine.platform.api.OperationalTelemetry telemetry) {
        this.telemetry = telemetry;
        base = URI.create(address);
        this.mapper = mapper;
        if (!"http".equals(base.getScheme())
                || !Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(base.getHost())
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null
                || !List.of("", "/").contains(base.getPath()))
            throw new IllegalArgumentException(
                    "Ollama endpoint must be a loopback HTTP server with no redirects");
    }

    public JsonNode post(String path, Object body) {
        var protectedBody = new com.fraudengine.platform.gateway.RedactionFilter().payload(mapper.valueToTree(body));
        java.util.function.Supplier<JsonNode> operation = () -> {
            if (telemetry != null && protectedBody.path("model").isTextual()) {
                String selected = protectedBody.path("model").asText();
                telemetry.version(com.fraudengine.platform.api.OperationalTelemetry.Version.MODEL,
                        "ollama:" + selected + "@" + digest(selected));
            }
            long started = System.nanoTime();
            var result = request(path, protectedBody);
            if (telemetry != null)
                telemetry.usage(Math.max(0, result.path("prompt_eval_count").asLong(0)),
                        Math.max(0, result.path("eval_count").asLong(0)),
                        result.path("prompt_eval_count").isIntegralNumber(),
                        Math.max(0, result.path("total_duration").asLong(System.nanoTime() - started)));
            return result;
        };
        return telemetry == null ? operation.get()
                : telemetry.observe(com.fraudengine.platform.api.OperationalTelemetry.Stage.PROVIDER, operation);
    }

    private JsonNode request(String path, Object body) {
        try {
            var builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(90));
            if (body == null)
                builder.GET();
            else
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new ModelUnavailableException(
                        "Local model request failed with HTTP "
                                + response.statusCode()
                                + "; check server and installed model");
            JsonNode parsed = mapper.readTree(response.body());
            if (parsed == null || !parsed.isObject())
                throw new ModelUnavailableException("Malformed local model response");
            return parsed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelUnavailableException("Local model call interrupted", e);
        } catch (java.io.IOException e) {
            throw new ModelUnavailableException(
                    "Local model unavailable; start Ollama and install configured models", e);
        }
    }

    public String digest(String model) {
        String normalized = model.contains(":") ? model : model + ":latest";
        for (var item : request("/api/tags", null).path("models")) {
            if (normalized.equals(item.path("name").asText())
                    || normalized.equals(item.path("model").asText())) {
                String digest = item.path("digest").asText();
                if (!digest.isBlank())
                    return digest;
            }
        }
        throw new ModelUnavailableException("Configured local model is not installed: " + model);
    }
}
