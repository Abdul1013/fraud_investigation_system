/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/ModelUsage.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Carry provider-reported usage with a model receipt without inventing missing counters.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-32
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Usage is accounting metadata only; it carries no prompt, evidence, credential or user identity.
 * ============================================================================
 */
package com.fraudengine.platform.api;
public record ModelUsage(boolean reported,long inputTokens,long outputTokens,long inferenceNanos) {
    public ModelUsage {if(inputTokens<0||outputTokens<0||inferenceNanos<0)throw new IllegalArgumentException("Invalid model usage");}
    public static ModelUsage unavailable() {return new ModelUsage(false,0,0,0);}
}
