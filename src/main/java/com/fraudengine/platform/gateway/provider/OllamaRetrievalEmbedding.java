/**
 * Semantic embeddings through Ollama's /api/embed endpoint. truncate=false rejects inputs
 * beyond model context instead of indexing a silently truncated source. The separate retrieval
 * index accepts actual model dimensions, and the digest-bearing spaceId separates model revisions.
 * Request/response validation is explicit; database code validates finite nonzero vectors.
 */
package com.fraudengine.platform.gateway.provider;

import com.fraudengine.platform.gateway.*;

import java.util.*;

public class OllamaRetrievalEmbedding implements RetrievalEmbeddingPort {
    private final OllamaLocalClient client;
    private final String model;

    public OllamaRetrievalEmbedding(OllamaLocalClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String spaceId() {
        return "ollama:" + model + "@" + client.digest(model);
    }

    @Override
    public boolean fixture() {
        return false;
    }

    @Override
    public float[] embed(String text) {
        var response = client.post("/api/embed", Map.of("model", model, "input", text, "truncate", false));
        var values = response.path("embeddings");
        if (!values.isArray() || values.size() != 1 || !values.get(0).isArray())
            throw new ModelUnavailableException("Malformed local embedding response");
        float[] vector = new float[values.get(0).size()];
        for (int i = 0; i < vector.length; i++) {
            if (!values.get(0).get(i).isNumber())
                throw new ModelUnavailableException("Non-numeric local embedding");
            vector[i] = values.get(0).get(i).floatValue();
        }
        return vector;
    }
}
