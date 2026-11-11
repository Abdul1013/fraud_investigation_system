/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/observability/OpenTelemetryConfig.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Bound metric cardinality in addition to fixed application stage labels.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Boot manages the OpenTelemetry bridge and OTLP exporters; no second tracer SDK is created.
 *   - Maximum series/tag counts prevent unrestricted HTTP request values from growing telemetry indefinitely.
 * ============================================================================
 */
package com.fraudengine.platform.observability;
import org.springframework.context.annotation.*;
import io.micrometer.core.instrument.config.MeterFilter;
@Configuration(proxyBeanMethods=false)
public class OpenTelemetryConfig {
    @Bean MeterFilter totalMetricBound() { return MeterFilter.maximumAllowableMetrics(2000); }
    @Bean MeterFilter routeMetricBound() { return MeterFilter.maximumAllowableTags("http.server.requests","uri",100,MeterFilter.deny()); }
    @Bean MeterFilter methodMetricBound() { return MeterFilter.maximumAllowableTags("http.server.requests","method",12,MeterFilter.deny()); }
}
