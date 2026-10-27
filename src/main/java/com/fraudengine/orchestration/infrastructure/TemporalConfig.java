/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/TemporalConfig.java
 * CONTEXT:     orchestration
 * LAYER:       config
 * PURPOSE:     Wire the pinned Temporal SDK explicitly and separate client delivery from worker execution.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Normal application profiles use durable orchestration; Week 28 compatibility tests select the local engine.
 *   - MCP is a local transport role and disables workflow workers; it does not require an unrelated polling process.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;

import com.fraudengine.orchestration.domain.*;
import io.temporal.serviceclient.*;
import io.temporal.client.*;
import io.temporal.worker.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="investigation.engine",havingValue="temporal",matchIfMissing=true)
public class TemporalConfig {
    public static final String TASK_QUEUE="fraud-investigations-v1";
    @Bean(destroyMethod="shutdownNow")
    @ConditionalOnMissingBean
    WorkflowServiceStubs workflowServiceStubs(@Value("${investigation.temporal-address:127.0.0.1:7233}")String address) {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(address)
                .setRpcTimeout(Duration.ofSeconds(3)).build());
    }
    @Bean @ConditionalOnMissingBean
    WorkflowClient workflowClient(WorkflowServiceStubs service,@Value("${investigation.temporal-namespace:default}")String namespace) {
        return WorkflowClient.newInstance(service,WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }
    @Bean(destroyMethod="shutdownNow")
    @ConditionalOnProperty(name="investigation.worker-enabled",havingValue="true",matchIfMissing=true)
    WorkerFactory workerFactory(WorkflowClient client,DurableTurnActivities activities,
            @Value("${spring.datasource.hikari.maximum-pool-size:10}") int pool,
            @Value("${investigation.activity-concurrency:2}") int requested) {
        if(pool<4||requested<1||requested>8)throw new IllegalArgumentException("Worker requires four database connections and 1–8 requested activities");
        var factory=WorkerFactory.newInstance(client);
        var worker=factory.newWorker(TASK_QUEUE,WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(Math.max(1,Math.min(Math.min(requested,8),pool/4))).setMaxConcurrentWorkflowTaskExecutionSize(4).build());
        worker.registerWorkflowImplementationTypes(InvestigationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        return factory;
    }
}
