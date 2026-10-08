package com.fraudengine.platform.gateway;

import com.fraudengine.platform.gateway.provider.LocalLearningEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
public class EmbeddingConfig {
    @Bean
    @ConditionalOnProperty(name = "knowledge.embedding-provider", havingValue = "local", matchIfMissing = true)
    public EmbeddingModel learningEmbeddingModel() {
        return new LocalLearningEmbeddingModel();
    }
}
