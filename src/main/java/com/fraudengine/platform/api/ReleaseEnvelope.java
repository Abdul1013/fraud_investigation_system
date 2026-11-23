/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/ReleaseEnvelope.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Pin a complete server-owned AI release for durable work.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Version identity is provenance, never permission. Every new deployment launch stores this immutable value.
 *   - Model and embedding references include content digests; retained aliases allow old investigations to continue.
 * ============================================================================
 */
package com.fraudengine.platform.api;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"_file", "_purpose", "_since", "_notes"})
public record ReleaseEnvelope(
    String releaseId,
    String promptReference,
    String modelVersion,
    int corpusVersion,
    String embeddingSpace,
    String retrievalConfiguration,
    double minimumSimilarity,
    String retrievalStrategy,
    boolean proposalsEnabled) {
  public ReleaseEnvelope {
    if (releaseId == null
        || !releaseId.matches("[a-z0-9-]{1,64}")
        || promptReference == null
        || !promptReference.matches("investigator-v[12]@[0-9a-f]{64}")
        || modelVersion == null
        || !modelVersion.matches("ollama:[a-zA-Z0-9._:/-]{1,120}@[0-9a-f]{64}")
        || embeddingSpace == null
        || !embeddingSpace.matches("ollama:[a-zA-Z0-9._:/-]{1,120}@[0-9a-f]{64}")
        || corpusVersion < 1
        || !Double.isFinite(minimumSimilarity)
        || minimumSimilarity < 0
        || minimumSimilarity > 1
        || !java.util.Set.of("VECTOR", "HYBRID").contains(retrievalStrategy)
        || !("week28-scoped-v1:min=" + minimumSimilarity + ":candidates=20:rrf=60")
            .equals(retrievalConfiguration))
      throw new IllegalArgumentException("Invalid immutable release envelope");
  }
}
