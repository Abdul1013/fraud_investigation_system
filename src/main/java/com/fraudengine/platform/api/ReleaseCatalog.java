/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/ReleaseCatalog.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Expose operator-selected releases through a public platform boundary.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The active release is selected by server configuration, never a model argument or ordinary API input.
 *   - record joins the launch transaction and rejects reusing a release ID with different bytes.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public interface ReleaseCatalog {
  ReleaseEnvelope active();

  void record(ReleaseEnvelope envelope);
}
