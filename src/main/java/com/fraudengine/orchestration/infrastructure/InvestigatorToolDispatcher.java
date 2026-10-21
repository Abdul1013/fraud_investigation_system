/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/InvestigatorToolDispatcher.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Dispatches strict argument schemas through public case, knowledge, and decision APIs.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - An explicit switch replaces reflection and rejects identity, approval, or arbitrary parameter fields.
 *   - Proposal creation reruns scoped retrieval and persists validated backend evidence, not model-supplied citations.
 *   - All failures are bounded structured tool results without stack traces or provider bodies.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;

import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.decision.api.DecisionApi;
import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.RetrievalRequest;
import com.fraudengine.orchestration.api.InvestigatorTools;
import com.fraudengine.platform.api.AccessDeniedException;
import com.fraudengine.platform.api.CurrentCaller;
import com.fraudengine.platform.api.Permission;
import com.fraudengine.platform.api.ToolCallingPort;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InvestigatorToolDispatcher implements InvestigatorTools {
    private final CaseApi cases;
    private final KnowledgeApi knowledge;
    private final DecisionApi decisions;
    private final CurrentCaller caller;
    private final com.fraudengine.audit.api.AuditApi audit;

    public InvestigatorToolDispatcher(CaseApi cases, KnowledgeApi knowledge, DecisionApi decisions, CurrentCaller caller, com.fraudengine.audit.api.AuditApi audit) {
        this.cases = cases; this.knowledge = knowledge; this.decisions = decisions; this.caller = caller; this.audit = audit;
    }

    @Override
    public List<ToolCallingPort.Definition> definitions() {
        var identity = caller.requireAuthenticated();
        List<ToolCallingPort.Definition> tools = new ArrayList<>();
        if (identity.hasPermission(Permission.READ_CASE)) tools.add(definition("get_case_details", "Read the bound case", Map.of()));
        if (identity.hasPermission(Permission.READ_EVIDENCE)) tools.add(definition("retrieve_case_evidence",
                "Read evidence allowed for the bound case", Map.of("query", text(2000))));
        if (identity.hasPermission(Permission.PROPOSE_ACTION)) tools.add(definition("propose_account_freeze",
                "Persist an unapproved simulated freeze proposal; does not freeze any account",
                Map.of("evidenceQuery", text(2000), "rationale", text(2000))));
        return List.copyOf(tools);
    }

    private Map<String, Object> text(int maximum) {
        return Map.of("type", "string", "minLength", 1, "maxLength", maximum);
    }

    private ToolCallingPort.Definition definition(String name, String description, Map<String, Object> properties) {
        return new ToolCallingPort.Definition(name, description, Map.of("type", "object", "properties", properties,
                "required", properties.keySet().stream().sorted().toList(), "additionalProperties", false),
                name.equals("propose_account_freeze") ? ToolCallingPort.Effect.PROPOSAL : ToolCallingPort.Effect.READ);
    }

    @Override
    public Result call(String caseId, ToolCallingPort.Call call, String modelVersion) {
        if (call == null || call.id() == null || call.id().isBlank() || call.id().length() > 100) {
            throw new IllegalArgumentException("Invalid tool-call correlation ID");
        }
        var identity = caller.requireAuthenticated();
        var result = dispatch(caseId, call, modelVersion);
        audit.observe(identity.subject(), result.error() ? "TOOL_DENIED" : "TOOL_COMPLETED", caseId, call.id());
        return result;
    }

    private Result dispatch(String caseId, ToolCallingPort.Call call, String modelVersion) {
        if (call.name() == null || call.name().length() > 100) {
            return new Result(call.id(), "invalid", true, Map.of("error", "Invalid tool name"));
        }
        try {
            var definition = definitions().stream().filter(tool -> tool.name().equals(call.name()))
                    .findFirst().orElseThrow(AccessDeniedException::new);
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) definition.schema().get("properties");
            if (call.arguments() == null || !call.arguments().keySet().equals(properties.keySet())) {
                return new Result(call.id(), call.name(), true, Map.of("error", "Invalid tool arguments"));
            }
            for (Object value : call.arguments().values()) {
                if (!(value instanceof String text) || text.isBlank() || text.length() > 2000) {
                    return new Result(call.id(), call.name(), true, Map.of("error", "Invalid tool arguments"));
                }
            }
            Object value = switch (call.name()) {
                case "get_case_details" -> cases.getCase(caseId);
                case "retrieve_case_evidence" -> knowledge.retrieveCaseEvidence(caseId,
                        request((String) call.arguments().get("query")));
                case "propose_account_freeze" -> {
                    var evidence = knowledge.retrieveCaseEvidence(caseId,
                            request((String) call.arguments().get("evidenceQuery")));
                    if (evidence.passages().isEmpty()) yield Map.of("status", "INSUFFICIENT_EVIDENCE");
                    yield decisions.propose(caseId, (String) call.arguments().get("rationale"), evidence, modelVersion);
                }
                default -> throw new AccessDeniedException();
            };
            return new Result(call.id(), call.name(), false, value);
        } catch (AccessDeniedException denied) {
            return new Result(call.id(), call.name(), true, Map.of("error", "Access denied"));
        } catch (RuntimeException error) {
            return new Result(call.id(), call.name(), true, Map.of("error", "Tool unavailable"));
        }
    }

    private RetrievalRequest request(String query) {
        return new RetrievalRequest(query, null, RetrievalRequest.Strategy.VECTOR, 5, null, null);
    }
}
