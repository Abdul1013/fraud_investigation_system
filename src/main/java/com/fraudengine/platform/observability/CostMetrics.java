/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/observability/CostMetrics.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Record real token counters separately from configured local compute estimates.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Local inference incurs operating cost; estimated compute USD is not a provider invoice or API charge.
 *   - Provider counts may be unavailable; unknown usage is counted explicitly rather than invented.
 * ============================================================================
 */
package com.fraudengine.platform.observability;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
@Component
public class CostMetrics {
    private final MeterRegistry meters;
    private final double localHourlyUsd;
    public CostMetrics(MeterRegistry meters,@Value("${observability.local-compute-usd-per-hour:0.08}")double localHourlyUsd) {
        if(!Double.isFinite(localHourlyUsd)||localHourlyUsd<0)throw new IllegalArgumentException("Invalid local compute assumption");
        this.meters=meters;this.localHourlyUsd=localHourlyUsd;
    }
    public void usage(long input,long output,boolean reported,long nanos) {
        if(input<0||output<0||nanos<0)throw new IllegalArgumentException("Invalid provider usage");
        if(reported) {
            meters.counter("fraud.model.tokens","direction","input","provider","local").increment(input);
            meters.counter("fraud.model.tokens","direction","output","provider","local").increment(output);
        }else meters.counter("fraud.model.usage.unavailable","provider","local").increment();
        meters.counter("fraud.local.compute.estimated.usd").increment(nanos/3_600_000_000_000.0*localHourlyUsd);
        meters.counter("fraud.provider.api.charged.usd","provider","local").increment(0);
    }
}
