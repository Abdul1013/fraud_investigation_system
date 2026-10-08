package com.fraudengine.knowledge.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway owns the vector schema; the adapter uses exactly the same table and
 * dimensions.
 */
@Configuration
public class VectorStoreConfig {
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbc, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbc, embeddingModel).dimensions(1536)
                .schemaName("knowledge").vectorTableName("vector_store")
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW).initializeSchema(false).build();
    }
}
