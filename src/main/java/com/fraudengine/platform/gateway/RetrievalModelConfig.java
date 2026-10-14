/**
 *  Spring bean wiring independently chooses the embedding and answer adapters.
 * Fixture/extractive defaults keep development runnable without downloads or paid calls; responses
 * label them. Selecting Ollama makes model availability a visible requirement, with no silent
 * fallback. Configuration switches do not alter Week 26 ingestion, which remains a separately
 * verified pipeline.
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
            ObjectMapper mapper) {
        return new OllamaLocalClient(url, mapper);
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
