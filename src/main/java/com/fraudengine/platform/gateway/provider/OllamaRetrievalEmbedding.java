/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/provider/OllamaRetrievalEmbedding.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Validate ordered native embedding batches before persistence.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-27
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - SINCE week-27; maximum 32 inputs bounds provider work and response memory.
 *   - The endpoint preserves input order; dimensions and finite nonzero vectors are checked.
 * ============================================================================
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
  public RetrievalEmbeddingPort pinned(String space) {
    if (space == null || !space.matches("ollama:[a-zA-Z0-9._:/-]{1,120}@[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid pinned embedding space");
    var selected = new OllamaRetrievalEmbedding(client, space.substring(7, space.lastIndexOf('@')));
    return new RetrievalEmbeddingPort() {
      public String spaceId() {
        if (!space.equals(selected.spaceId()))
          throw new ModelUnavailableException("Pinned embedding model unavailable");
        return space;
      }

      public boolean fixture() {
        return false;
      }

      public float[] embed(String text) {
        spaceId();
        var vector = selected.embed(text);
        spaceId();
        return vector;
      }

      public List<float[]> embedBatch(List<String> texts) {
        spaceId();
        var vectors = selected.embedBatch(texts);
        spaceId();
        return vectors;
      }
    };
  }

  @Override
  public float[] embed(String text) {
    return embedBatch(List.of(text)).getFirst();
  }

  @Override
  public List<float[]> embedBatch(List<String> texts) {
    if (texts.isEmpty() || texts.size() > 32)
      throw new IllegalArgumentException("Embedding batch must contain 1–32 inputs");
    var values =
        client
            .post("/api/embed", Map.of("model", model, "input", texts, "truncate", false))
            .path("embeddings");
    if (!values.isArray() || values.size() != texts.size())
      throw new ModelUnavailableException("Malformed local embedding batch");
    var vectors = new ArrayList<float[]>();
    int dimensions = -1;
    for (var row : values) {
      if (!row.isArray() || row.isEmpty() || (dimensions != -1 && dimensions != row.size()))
        throw new ModelUnavailableException("Inconsistent embedding dimensions");
      dimensions = row.size();
      var vector = new float[dimensions];
      double norm = 0;
      for (int i = 0; i < dimensions; i++) {
        if (!row.get(i).isNumber())
          throw new ModelUnavailableException("Non-numeric local embedding");
        vector[i] = row.get(i).floatValue();
        if (!Float.isFinite(vector[i]))
          throw new ModelUnavailableException("Non-finite local embedding");
        norm += (double) vector[i] * vector[i];
      }
      if (norm == 0) throw new ModelUnavailableException("Zero local embedding");
      vectors.add(vector);
    }
    return List.copyOf(vectors);
  }
}
