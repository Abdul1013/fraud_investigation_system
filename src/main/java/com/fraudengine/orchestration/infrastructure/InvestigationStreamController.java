/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/InvestigationStreamController.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Stream bounded persisted progress while continuously rechecking case access.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Twenty live connections and a thirty-second lifetime bound resources; reconnect using Last-Event-ID.
 *   - Only server-owned state and sequence are sent; event detail and model output are deliberately excluded.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;

import com.fraudengine.orchestration.api.OrchestrationApi;
import com.fraudengine.platform.api.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class InvestigationStreamController {
    private final OrchestrationApi runs;
    private final AuthenticatedWork work;
    private final Semaphore connections = new Semaphore(20);

    public InvestigationStreamController(OrchestrationApi runs, AuthenticatedWork work) {
        this.runs = runs;
        this.work = work;
    }

    @GetMapping(value = "/v1/investigations/{id}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable UUID id,
            @RequestHeader(value = "Last-Event-ID", defaultValue = "0") int cursor,
            jakarta.servlet.http.HttpServletRequest request) {
        if (cursor < 0)
            throw new IllegalArgumentException("Invalid event cursor");
        runs.get(id);
        if (!connections.tryAcquire())
            throw new CapacityRejectedException(5);
        request.setAttribute("fraud.authorized-progress-stream", Boolean.TRUE);
        var emitter = new SseEmitter(30_000L);
        var closed = new AtomicBoolean();
        Runnable close = () -> {
            if (closed.compareAndSet(false, true))
                connections.release();
        };
        emitter.onCompletion(close);
        emitter.onTimeout(() -> {
            close.run();
            emitter.complete();
        });
        emitter.onError(e -> close.run());
        try {
            var task = work.capture(() -> {
                int last = cursor;
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(29).toNanos();
                try {
                    while (!closed.get() && System.nanoTime() < deadline) {
                        var run = runs.get(id); // Fresh identity and case grants on every iteration.
                        for (var event : runs.events(id))
                            if (event.sequence() > last) {
                                emitter.send(SseEmitter.event().id(Integer.toString(event.sequence())).name("progress")
                                        .data(Map.of("sequence", event.sequence())));
                                last = event.sequence();
                            }
                        emitter.send(SseEmitter.event().name("state")
                                .data(Map.of("state", run.state(), "version", run.version())));
                        Thread.sleep(1000);
                    }
                    emitter.complete();
                } catch (AccessDeniedException denied) {
                    try {
                        emitter.send(SseEmitter.event().name("access_revoked").data(Map.of("status", "closed")));
                    } catch (Exception ignored) {
                    }
                    emitter.complete();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    emitter.complete();
                } catch (Exception disconnected) {
                    emitter.complete();
                } finally {
                    close.run();
                }
            });
            Thread.ofVirtual().name("investigation-progress").start(task);
        } catch (RuntimeException failed) {
            close.run();
            emitter.complete();
            throw failed;
        }
        return emitter;
    }
}
