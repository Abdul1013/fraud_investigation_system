/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/RetrievalModelConfig.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Choose local/fixture adapters behind the model gateway and wire operational accounting.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-27
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Provider implementations remain in the gateway; no cloud fallback is selected.
 *   - The shared local client enforces outbound privacy and reports actual usage when available.
 * ============================================================================
 */
package com.fraudengine.platform.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.platform.gateway.provider.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class RetrievalModelConfig {
    @Bean
    public OllamaLocalClient localRetrievalClient(
            @Value("${knowledge.retrieval.ollama-url:http://127.0.0.1:11434}") String url,
            ObjectMapper mapper,com.fraudengine.platform.api.OperationalTelemetry telemetry) {
        return new OllamaLocalClient(url,mapper,telemetry);
    }

    @Bean
    public RetrievalEmbeddingPort retrievalEmbeddingPort(
            OllamaLocalClient client,
            @Value("${knowledge.retrieval.embedding-provider:fixture}") String provider,
            @Value("${knowledge.retrieval.embedding-model:embeddinggemma}") String model) {
        return switch (provider) {
            case "fixture" -> new FixtureRetrievalEmbedding();
            case "ollama" -> new OllamaRetrievalEmbedding(client, model);
            default ->
                    throw new IllegalArgumentException(
                            "Retrieval embedding provider must be fixture or ollama");
        };
    }

    @Bean
    public EvidenceAnswerPort evidenceAnswerPort(
            OllamaLocalClient client,
            ObjectMapper mapper,
            @Value("${knowledge.retrieval.answer-provider:extractive}") String provider,
            @Value("${knowledge.retrieval.answer-model:gemma3:4b}") String model) {
        return switch (provider) {
            case "extractive" -> new ExtractiveAnswerAdapter();
            case "ollama" -> new OllamaEvidenceAnswer(client, model, mapper);
            default ->
                    throw new IllegalArgumentException(
                            "Answer provider must be extractive or ollama");
        };
    }
}
