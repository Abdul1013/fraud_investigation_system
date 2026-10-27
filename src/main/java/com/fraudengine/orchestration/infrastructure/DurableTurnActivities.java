/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/DurableTurnActivities.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Execute one bounded turn with persistent response/checkpoint receipts and fresh case authorization.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-29
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Model calls may repeat if a crash happens before their receipt commits; consequential effects still require separate human authorization.
 *   - Proposal operation keys are stable across retries, and no approval/execute method is called here.
 *   - Only opaque outcome strings enter Temporal history; transcripts remain in the application database.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.orchestration.domain.InvestigationActivities;
import com.fraudengine.orchestration.api.InvestigatorTools;
import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.decision.api.dto.RecommendationDto;
import com.fraudengine.platform.api.*;
import io.temporal.failure.ApplicationFailure;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DurableTurnActivities implements InvestigationActivities {
    private final DurableInvestigationStore store;
    private final InvestigationRepository runs;
    private final LocalServiceIdentity identity;
    private final CaseApi cases;
    private final ToolCallingPort model;
    private final InvestigatorTools tools;
    private final ObjectMapper mapper;
    private final AuthenticatedWork authenticated;
    private final int callTimeout;
    private final OperationalTelemetry telemetry;
    private final java.util.concurrent.ExecutorService calls=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    public DurableTurnActivities(DurableInvestigationStore store,InvestigationRepository runs,LocalServiceIdentity identity,
            CaseApi cases,ToolCallingPort model,InvestigatorTools tools,ObjectMapper mapper,AuthenticatedWork authenticated,OperationalTelemetry telemetry,
            @org.springframework.beans.factory.annotation.Value("${investigation.call-timeout-seconds:20}") int callTimeout) {
        this.telemetry=telemetry;this.store=store;this.runs=runs;this.identity=identity;this.cases=cases;this.model=model;this.tools=tools;this.mapper=mapper;this.authenticated=authenticated;this.callTimeout=callTimeout;
        if(callTimeout<1||callTimeout>20)throw new IllegalArgumentException("Invalid call timeout");
    }
    @Override public String advance(String id,int step) {
        UUID run=UUID.fromString(id);
        try {
            var delegation=store.delegation(run);
            return identity.call(delegation.subject(),()->telemetry.continueTrace(store.traceParent(run),OperationalTelemetry.Stage.ACTIVITY,()->advanceScoped(run,step,delegation)));
        } catch (AccessDeniedException denied) {
            throw ApplicationFailure.newNonRetryableFailure("Delegation or case access denied","ACCESS_DENIED");
        }
    }
    private String advanceScoped(UUID id,int step,DurableInvestigationStore.Delegation delegation) {
        if (step<0 || step>=6) throw ApplicationFailure.newNonRetryableFailure("Invalid step","INVALID_STEP");
        if (!"C2".equals(cases.evidenceScope(delegation.caseId()).classificationCeiling())) throw new AccessDeniedException();
        String completed=store.outcome(id,step);
        if (completed!=null) return completed;
        var run=runs.get(id);
        if (step==0) runs.transition(id,"NEW","INVESTIGATING",null,"Durable worker started");
        Instant deadline=store.started(id).plusSeconds(120);
        if (!deadline.isAfter(Instant.now())) throw ApplicationFailure.newNonRetryableFailure("Active-phase deadline expired","INVALID_STEP");
        var frozenPrompt=store.prompt(id);
        var release=store.release(id);
        Checkpoint checkpoint=step==0?new Checkpoint(new ArrayList<>(List.of(
                new ToolCallingPort.Message("system",frozenPrompt.text(),null,null),
                new ToolCallingPort.Message("user",run.goal(),null,null))),0,0,new HashSet<>(),null,frozenPrompt.reference())
                :read(store.checkpoint(id,step-1),Checkpoint.class);
        String encoded=store.response(id,step);
        if (encoded==null) {
            var definitions=tools.definitions();
            var response=bounded(()->telemetry.observe(OperationalTelemetry.Stage.MODEL,()->{
                var observed=release==null?model.turn(List.copyOf(checkpoint.messages()),definitions):model.turnPinned(List.copyOf(checkpoint.messages()),definitions,release.modelVersion());
                telemetry.version(OperationalTelemetry.Version.PROMPT,frozenPrompt.reference());
                telemetry.version(OperationalTelemetry.Version.MODEL,observed.modelVersion());return observed;
            }),deadline);
            store.response(id,step,json(response));
            encoded=store.response(id,step);
        }
        var response=read(encoded,ToolCallingPort.Response.class);
        if (response.calls()==null || response.calls().size()>1 || response.modelVersion()==null || response.modelVersion().isBlank() || response.modelVersion().length()>300
                || (checkpoint.modelVersion()!=null && !checkpoint.modelVersion().equals(response.modelVersion())))
            throw ApplicationFailure.newNonRetryableFailure("Invalid model response","INVALID_STEP");
        if (response.calls().isEmpty()) return complete(id,step,checkpoint,"INSUFFICIENT_EVIDENCE",null);
        var call=response.calls().getFirst();
        int reads=checkpoint.reads()+1, proposals=checkpoint.proposals()+("propose_account_freeze".equals(call.name())?1:0);
        var seen=new HashSet<>(checkpoint.seen());
        if (reads>3 || proposals>1 || !seen.add(call.name()+json(call.arguments())))
            throw ApplicationFailure.newNonRetryableFailure("Tool budget exceeded","INVALID_STEP");
        var result=bounded(()->tools.callVersioned(delegation.caseId(),call,new AiVersionEnvelope(response.modelVersion(),
                checkpoint.promptReference()==null?"week28-investigator-v1":checkpoint.promptReference(),release),id+"/"+step),deadline);
        if (result.error()) throw ApplicationFailure.newNonRetryableFailure("Tool rejected","INVALID_STEP");
        runs.toolEvent(id,"callId="+call.id()+"; tool="+call.name()+"; error=false");
        var messages=new ArrayList<>(checkpoint.messages());
        messages.add(new ToolCallingPort.Message("assistant","",call,null));
        messages.add(new ToolCallingPort.Message("tool",json(result),null,call.name()));
        var next=new Checkpoint(messages,reads,proposals,seen,response.modelVersion(),checkpoint.promptReference());
        if (result.value() instanceof RecommendationDto proposal)
            return complete(id,step,next,"PENDING_APPROVAL",proposal.id());
        store.finishStep(id,step,json(next),"CONTINUE");
        return "CONTINUE";
    }
    private String complete(UUID id,int step,Checkpoint checkpoint,String outcome,UUID proposal) {
        // The store commits checkpoint, state/event, and reservation release together.
        store.complete(id,step,json(checkpoint),outcome,proposal);
        return outcome;
    }
    @Override public void finish(String id,String outcome) { store.systemFinish(UUID.fromString(id),outcome); }
    private <T>T bounded(java.util.concurrent.Callable<T> action,Instant deadline) {
        long waitMillis=Math.min(callTimeout*1000L,java.time.Duration.between(Instant.now(),deadline).toMillis());
        if(waitMillis<=0)throw ApplicationFailure.newNonRetryableFailure("Active-phase deadline expired","INVALID_STEP");
        var pending=new java.util.concurrent.FutureTask<T>(action);
        calls.execute(authenticated.capture(pending));
        try { return pending.get(waitMillis,java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Activity interrupted",interrupted); }
        catch(java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) { throw new IllegalStateException("Activity operation failed",failure); }
        finally { if(!pending.isDone())pending.cancel(true); }
    }
    @jakarta.annotation.PreDestroy public void close() { calls.shutdownNow(); }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(java.io.IOException error) { throw new IllegalStateException("Cannot encode durable receipt",error); }
    }
    private <T>T read(String json,Class<T> type) {
        try { return mapper.readValue(json,type); }
        catch(java.io.IOException error) { throw new IllegalStateException("Cannot decode durable receipt",error); }
    }
    public record Checkpoint(List<ToolCallingPort.Message> messages,int reads,int proposals,Set<String> seen,String modelVersion,String promptReference) {}
}
