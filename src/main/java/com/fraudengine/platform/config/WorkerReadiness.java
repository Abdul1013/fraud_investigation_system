/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/config/WorkerReadiness.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Refresh a private readiness heartbeat after database and workflow health checks.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - A stale or absent file fails the container probe; an old successful startup is not perpetual readiness.
 *   - Workers expose no HTTP port. The marker contains only a timestamp and is removed at shutdown.
 * ============================================================================
 */
package com.fraudengine.platform.config;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.actuate.health.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class WorkerReadiness {
  private final HealthEndpoint health;
  private final AtomicBoolean ready = new AtomicBoolean();
  private final Path marker = Path.of("/tmp/fraud-worker-ready");

  public WorkerReadiness(HealthEndpoint health) {
    this.health = health;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void started() {
    ready.set(true);
    refresh();
  }

  @Scheduled(fixedDelay = 5000)
  public void refresh() {
    try {
      if (ready.get() && Status.UP.equals(health.health().getStatus()))
        Files.writeString(marker, Long.toString(System.currentTimeMillis()));
      else Files.deleteIfExists(marker);
    } catch (Exception unavailable) {
      try {
        Files.deleteIfExists(marker);
      } catch (java.io.IOException ignored) {
      }
    }
  }

  @EventListener(ContextClosedEvent.class)
  public void stopped() {
    ready.set(false);
    try {
      Files.deleteIfExists(marker);
    } catch (java.io.IOException ignored) {
    }
  }
}
