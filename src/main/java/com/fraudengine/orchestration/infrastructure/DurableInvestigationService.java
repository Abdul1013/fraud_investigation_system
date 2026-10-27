/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/DurableInvestigationService.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Persist launch intent and return a stable identifier before contacting Temporal.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-29
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The launch row, delegation, and outbox are committed together; no distributed database/Temporal transaction is claimed.
 *   - Existing interrupted local runs are not silently replayed. Only newly created durable launches enter the outbox.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;

import com.fraudengine.orchestration.api.OrchestrationApi;
import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.decision.api.DecisionApi;
import com.fraudengine.platform.api.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(name="investigation.engine", havingValue="temporal", matchIfMissing=true)
public class DurableInvestigationService implements OrchestrationApi {
    private final InvestigationRepository runs;
    private final DurableInvestigationStore durable;
    private final CaseApi cases;
    private final DecisionApi decisions;
    private final CurrentCaller identity;
    private final ReadAdmission admission;
    public DurableInvestigationService(InvestigationRepository runs, DurableInvestigationStore durable,
            CaseApi cases, DecisionApi decisions, CurrentCaller identity, ReadAdmission admission) {
        this.admission=admission;
        this.runs=runs; this.durable=durable; this.cases=cases; this.decisions=decisions; this.identity=identity;
    }
    @Override public Run launch(String caseId, String goal, String key) {
        var caller=identity.requireAuthenticated();
        if (!caller.hasPermission(Permission.PROPOSE_ACTION)) throw new AccessDeniedException();
        requireCase(caseId);
        if (goal==null || goal.isBlank() || goal.length()>2000 || key==null || key.isBlank() || key.length()>100)
            throw new IllegalArgumentException("Invalid launch");
        admission.requireLaunchBudget();
        return durable.launch(caseId,caller.subject(),goal,key);
    }
    @Override public Run get(UUID id) {
        var run=runs.get(id).run(); requireCase(run.caseId());
        if (run.recommendationId()!=null) return new Run(run.id(),run.caseId(),
                decisions.review(run.recommendationId()).recommendation().state(),run.version(),run.recommendationId());
        return run;
    }
    @Override public List<Event> events(UUID id) { get(id); return runs.events(id); }
    private void requireCase(String caseId) {
        if (!"C2".equals(cases.evidenceScope(caseId).classificationCeiling())) throw new AccessDeniedException();
    }
}
