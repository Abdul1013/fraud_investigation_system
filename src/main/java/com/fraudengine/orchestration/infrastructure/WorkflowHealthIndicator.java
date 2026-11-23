/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/WorkflowHealthIndicator.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Expose bounded Temporal connectivity in readiness without disclosing administration details.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only health status is exposed; failure messages and workflow payloads are omitted.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;

import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.boot.actuate.health.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("workflow")
@ConditionalOnProperty(
    name = "investigation.engine",
    havingValue = "temporal",
    matchIfMissing = true)
public class WorkflowHealthIndicator implements HealthIndicator {
  private final WorkflowServiceStubs service;
  private final String namespace;

  public WorkflowHealthIndicator(
      WorkflowServiceStubs service,
      @org.springframework.beans.factory.annotation.Value(
              "${investigation.temporal-namespace:default}")
          String namespace) {
    this.service = service;
    this.namespace = namespace;
  }

  @Override
  public Health health() {
    try {
      service
          .blockingStub()
          .withDeadlineAfter(2, java.util.concurrent.TimeUnit.SECONDS)
          .describeNamespace(
              io.temporal.api.workflowservice.v1.DescribeNamespaceRequest.newBuilder()
                  .setNamespace(namespace)
                  .build());
      return Health.up().build();
    } catch (RuntimeException unavailable) {
      return Health.down().build();
    }
  }
}
