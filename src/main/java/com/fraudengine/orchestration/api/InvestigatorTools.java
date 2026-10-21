/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/api/InvestigatorTools.java
 * CONTEXT:     orchestration
 * LAYER:       api
 * PURPOSE:     Provides only the three allowlisted investigator capabilities to transport adapters.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Case identity is bound outside model arguments and checked again by owning application services.
 *   - Approval and execution are absent from this contract.
 * ============================================================================
 */
package com.fraudengine.orchestration.api;

import com.fraudengine.platform.api.ToolCallingPort;
import java.util.List;

public interface InvestigatorTools {
    List<ToolCallingPort.Definition> definitions();
    Result call(String caseId, ToolCallingPort.Call call, String modelVersion);
    record Result(String callId, String toolName, boolean error, Object value) {}
}
