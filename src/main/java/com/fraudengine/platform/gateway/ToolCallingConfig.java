/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/ToolCallingConfig.java
 * CONTEXT:     platform
 * LAYER:       config
 * PURPOSE:     Selects local tool calling explicitly with no silent fixture or cloud fallback.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The default adapter rejects launches requiring a model until local Ollama is selected.
 * ============================================================================
 */
package com.fraudengine.platform.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.platform.api.ToolCallingPort;
import com.fraudengine.platform.gateway.provider.OllamaLocalClient;
import com.fraudengine.platform.gateway.provider.OllamaToolCallingAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ToolCallingConfig {
    @Bean
    ToolCallingPort toolCallingPort(OllamaLocalClient client, ObjectMapper mapper,
            @Value("${investigation.model-provider:disabled}") String provider,
            @Value("${investigation.model:qwen2.5:1.5b}") String model) {
        return switch (provider) {
            case "ollama" -> new OllamaToolCallingAdapter(client, model, mapper);
            case "disabled" -> (messages, tools) -> { throw new ModelUnavailableException("Investigation model is not configured"); };
            default -> throw new IllegalArgumentException("Investigation provider must be disabled or ollama");
        };
    }
}
