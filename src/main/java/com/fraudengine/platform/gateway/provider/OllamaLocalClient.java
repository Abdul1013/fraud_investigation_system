/**
 * Minimal local-only HTTP adapter using the existing JDK and Jackson dependencies.
 * Loopback validation and disabled redirects keep C2 passages on this machine; no external API
 * credentials or silent cloud fallback are used. The installed model digest is resolved for version
 * evidence. Timeouts and HTTP failures surface as 503 setup errors rather than empty answers or raw
 * provider payloads.
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
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public OllamaLocalClient(String address, ObjectMapper mapper) {
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
        return request(path, body);
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
