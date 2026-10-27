/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/WorkflowOutboxDelivery.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Deliver committed launch/outcome intents with correlated spans and bounded queue gauges.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-29
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Stable workflow IDs still reject duplicate delivery; observations never grant execution authority.
 *   - Only owning-context SQL and public decision-outbox APIs are used. Credentials never enter signals.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;
import com.fraudengine.decision.api.DecisionOutcomes;
import com.fraudengine.orchestration.domain.InvestigationWorkflow;
import com.fraudengine.platform.api.OperationalTelemetry;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name="investigation.engine",havingValue="temporal",matchIfMissing=true)
public class WorkflowOutboxDelivery {
    private final JdbcTemplate jdbc;
    private final WorkflowClient client;
    private final DecisionOutcomes decisions;
    private final DurableInvestigationStore store;
    private final OperationalTelemetry telemetry;
    public WorkflowOutboxDelivery(JdbcTemplate jdbc,WorkflowClient client,DecisionOutcomes decisions,DurableInvestigationStore store,OperationalTelemetry telemetry) {
        this.jdbc=jdbc;this.client=client;this.decisions=decisions;this.store=store;this.telemetry=telemetry;
    }
    @Scheduled(fixedDelayString="${investigation.outbox-delay-ms:1000}")
    public void deliver() {
        telemetry.backlog(OperationalTelemetry.Queue.LAUNCH,jdbc.queryForObject("SELECT count(*) FROM orchestration.launch_outbox WHERE delivered_at IS NULL",Long.class));
        var launches=jdbc.queryForList("SELECT run_id FROM orchestration.launch_outbox WHERE delivered_at IS NULL ORDER BY run_id LIMIT 50",UUID.class);
        for(var id:launches) {
            try {telemetry.continueTrace(store.traceParent(id),OperationalTelemetry.Stage.OUTBOX,()->{launch(id);return null;});}
            catch(RuntimeException unavailable) {jdbc.update("UPDATE orchestration.launch_outbox SET attempts=attempts+1 WHERE run_id=?",id);}
        }
        for(var event:decisions.pending(100)) {
            try {
                if(event.operationKey()!=null) {
                    UUID id=UUID.fromString(event.operationKey().split("/",2)[0]);
                    telemetry.continueTrace(store.traceParent(id),OperationalTelemetry.Stage.OUTBOX,()->{
                        client.newWorkflowStub(InvestigationWorkflow.class,id.toString()).decision(event.state());return null;
                    });
                }
                decisions.delivered(event.sequence());
            } catch(RuntimeException unavailable) { /* committed intent remains for redelivery */ }
        }
    }
    private void launch(UUID id) {
        var workflow=client.newWorkflowStub(InvestigationWorkflow.class,WorkflowOptions.newBuilder()
            .setWorkflowId(id.toString()).setTaskQueue(TemporalConfig.TASK_QUEUE)
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
            .setWorkflowExecutionTimeout(Duration.ofDays(8)).build());
        try {WorkflowClient.start(workflow::run,id.toString());}
        catch(WorkflowExecutionAlreadyStarted duplicate) { /* acknowledgment was lost; no duplicate workflow */ }
        jdbc.update("UPDATE orchestration.launch_outbox SET delivered_at=now(),attempts=attempts+1 WHERE run_id=?",id);
    }
}
