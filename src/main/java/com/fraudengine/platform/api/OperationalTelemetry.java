/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/OperationalTelemetry.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Expose bounded lifecycle observations without leaking telemetry implementations into other contexts.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-32
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only server-defined stage names become metric labels. No query, case, user or credential is accepted as a tag.
 *   - Parent strings contain only W3C trace identifiers; they convey no authentication authority.
 * ============================================================================
 */
package com.fraudengine.platform.api;
import java.util.function.Supplier;
public interface OperationalTelemetry {
    enum Stage { API, OUTBOX, ACTIVITY, RETRIEVAL, MODEL, PROVIDER, TOOL, APPROVAL, EXECUTION }
    enum Queue { LAUNCH, DECISION }
    enum Version { PROMPT, MODEL, RETRIEVAL }
    void version(Version kind,String value);
    <T>T observe(Stage stage,Supplier<T> operation);
    <T>T continueTrace(String parent,Stage stage,Supplier<T> operation);
    String parent();
    String traceId();
    void backlog(Queue queue,long pending);
    void usage(long inputTokens,long outputTokens,boolean reported,long inferenceNanos);
}
