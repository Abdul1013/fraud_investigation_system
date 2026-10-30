/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/PromptCatalog.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Expose immutable, digest-bearing prompt versions without provider dependencies.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-30
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 30
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Prompt selection comes from operator configuration; tool arguments cannot select authority or replace the system prompt.
 *   - Published versions are resource-manifest checked and running jobs persist their selected body/reference.
 * ============================================================================
 */
package com.fraudengine.platform.api;

public interface PromptCatalog {
    Prompt active();
    Prompt load(String version);
    Prompt byReference(String reference);
    record Prompt(String version,String sha256,String text) {
        public String reference() { return version+"@"+sha256; }
    }
}
