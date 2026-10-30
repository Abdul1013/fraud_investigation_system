/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/AiVersionEnvelope.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Carry server-owned model and prompt identity together into proposal persistence.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-30
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 30
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - This value is provenance, never authentication proof and never an ordinary request-body field.
 *   - Retrieval/corpus/source versions remain in the separately validated evidence snapshot.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public record AiVersionEnvelope(
    String modelVersion, String promptReference, ReleaseEnvelope release) {
  public AiVersionEnvelope(String model, String prompt) {
    this(model, prompt, null);
  }

  public AiVersionEnvelope {
    if (release != null
        && (!release.modelVersion().equals(modelVersion)
            || !release.promptReference().equals(promptReference)))
      throw new IllegalArgumentException("Release version mismatch");
    if (modelVersion == null
        || modelVersion.isBlank()
        || modelVersion.length() > 300
        || promptReference == null
        || promptReference.isBlank()
        || promptReference.length() > 300)
      throw new IllegalArgumentException("Invalid AI version envelope");
  }
}
