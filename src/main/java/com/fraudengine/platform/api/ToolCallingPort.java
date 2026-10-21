/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/ToolCallingPort.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Defines provider-neutral model turns without allowing automatic execution.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The model may request a tool; only the bounded orchestration dispatcher may execute it.
 *   - Tool results are appended as data messages, not elevated system instructions.
 * ============================================================================
 */
package com.fraudengine.platform.api;

import java.util.List;
import java.util.Map;

public interface ToolCallingPort {
    Response turn(List<Message> messages, List<Definition> tools);

    enum Effect { READ, PROPOSAL }
    record Definition(String name, String description, Map<String, Object> schema, Effect effect) {
        public Definition(String name, String description, Map<String, Object> schema) {
            this(name, description, schema, Effect.READ);
        }
    }
    record Call(String id, String name, Map<String, Object> arguments) {}
    record Message(String role, String content, Call call, String toolName) {}
    record Response(String content, List<Call> calls, String modelVersion) {}
}
