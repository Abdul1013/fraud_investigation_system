/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/RetrievalEmbeddingPort.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Expose ordered embedding batches without provider types.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-27
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - SINCE week-27; the default preserves adapters while native implementations reduce requests.
 *   - A batch must retain input ordering and one vector per input.
 * ============================================================================
 */
package com.fraudengine.platform.gateway;

public interface RetrievalEmbeddingPort {
  float[] embed(String text);

  default java.util.List<float[]> embedBatch(java.util.List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }

  default RetrievalEmbeddingPort pinned(String space) {
    if (!space.equals(spaceId()))
      throw new ModelUnavailableException("Pinned embedding space unavailable");
    return this;
  }

  String spaceId();

  boolean fixture();
}
