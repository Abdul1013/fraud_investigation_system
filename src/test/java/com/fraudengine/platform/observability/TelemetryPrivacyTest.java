/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/observability/TelemetryPrivacyTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Verify real SDK parent propagation, bounded labels and sanitized failures.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-32
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The in-process exporter observes actual SDK spans; no native-model or long-window SLO claim is made.
 *   - An exception carries a synthetic restricted sentinel so accidental exception-body export is detectable.
 * ============================================================================
 */
package com.fraudengine.platform.observability;

import static org.assertj.core.api.Assertions.*;
import com.fraudengine.platform.api.OperationalTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.*;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class TelemetryPrivacyTest {
    @Test
    void asyncSpansKeepTheirParentWithoutExportingExceptionContentsOrCallerLabels() throws Exception {
        List<SpanData> spans = new CopyOnWriteArrayList<>();
        SpanExporter exporter = new SpanExporter() {
            public CompletableResultCode export(Collection<SpanData> batch) {
                spans.addAll(batch);
                return CompletableResultCode.ofSuccess();
            }

            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
        var meters = new SimpleMeterRegistry();
        try (var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            var telemetry = new LifecycleTelemetry(OpenTelemetrySdk.builder().setTracerProvider(provider).build(),
                    meters, new CostMetrics(meters, .08), "week32-v1");
            telemetry.observe(OperationalTelemetry.Stage.API, () -> {
                telemetry.version(OperationalTelemetry.Version.MODEL, "RESTRICTED_C3_SENTINEL");
                String parent = telemetry.parent();
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    executor.submit(() -> telemetry.continueTrace(parent, OperationalTelemetry.Stage.ACTIVITY, () -> {
                        throw new IllegalArgumentException("RESTRICTED_C3_SENTINEL with confidential query");
                    })).get();
                } catch (Exception expected) {
                    assertThat(expected).isInstanceOf(ExecutionException.class);
                }
                return null;
            });
            assertThat(spans).hasSize(2);
            assertThat(spans).extracting(SpanData::getTraceId).containsOnly(spans.getFirst().getTraceId());
            var root = spans.stream().filter(span -> "fraud.api".equals(span.getName())).findFirst().orElseThrow();
            var activity = spans.stream().filter(span -> "fraud.activity".equals(span.getName())).findFirst()
                    .orElseThrow();
            assertThat(activity.getParentSpanId()).isEqualTo(root.getSpanId());
            for (var span : spans) {
                assertThat(span.getAttributes().asMap().keySet()).allSatisfy(
                        key -> assertThat(key.getKey()).isIn("fraud.release", "fraud.outcome", "fraud.model.version"));
                assertThat(span.getAttributes().toString()).doesNotContain("RESTRICTED_C3_SENTINEL");
                assertThat(span.getEvents()).isEmpty();
                assertThat(span.getStatus().getDescription()).doesNotContain("RESTRICTED_C3_SENTINEL",
                        "confidential query");
            }
            telemetry.usage(12, 3, true, 1_000_000_000L);
            assertThat(meters.get("fraud.model.tokens").tag("direction", "input").counter().count()).isEqualTo(12);
            assertThat(meters.get("fraud.local.compute.estimated.usd").counter().count()).isPositive();
            assertThat(meters.get("fraud.provider.api.charged.usd").counter().count()).isZero();
            assertThatThrownBy(() -> telemetry.continueTrace("secret-not-a-trace", OperationalTelemetry.Stage.ACTIVITY,
                    () -> null)).isInstanceOf(IllegalArgumentException.class);
            meters.getMeters()
                    .forEach(meter -> assertThat(meter.getId().getTags()).allSatisfy(tag -> assertThat(tag.getKey())
                            .isIn("stage", "outcome", "release", "queue", "direction", "provider")));
        } finally {
            meters.close();
        }
    }

    @Test
    void frameworkBodyLogsDoNotEmitRestrictedRequestValues() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger("org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor");
        var previous = logger.getLevel();
        var captured = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            new TelemetryLogPolicy().run(new org.springframework.boot.DefaultApplicationArguments());
            logger.debug("Read body {}", "RESTRICTED_C3_SENTINEL");
            logger.warn("Rejected value {}", "RESTRICTED_C3_SENTINEL");
            assertThat(captured.list).isEmpty();
        } finally {
            logger.detachAppender(captured);
            logger.setLevel(previous);
            captured.stop();
        }
    }

}
