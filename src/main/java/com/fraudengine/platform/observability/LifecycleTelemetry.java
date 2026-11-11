/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/observability/LifecycleTelemetry.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Create sanitized correlated spans and bounded operation metrics across asynchronous boundaries.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-32
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Exception messages and evidence are never recorded in spans; only a fixed outcome and release label are exported.
 *   - W3C parent input is strictly parsed and strips baggage, credentials and arbitrary trace-state values.
 * ============================================================================
 */
package com.fraudengine.platform.observability;
import com.fraudengine.platform.api.OperationalTelemetry;
import io.micrometer.core.instrument.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class LifecycleTelemetry implements OperationalTelemetry {
    private final io.opentelemetry.api.trace.Tracer tracer;
    private final MeterRegistry meters;
    private final CostMetrics costs;
    private final String release;
    private final EnumMap<Queue,AtomicLong> queues=new EnumMap<>(Queue.class);
    public LifecycleTelemetry(OpenTelemetry telemetry,MeterRegistry meters,CostMetrics costs,
            @Value("${observability.release:week32-v1}")String release) {
        if(!release.matches("week[0-9]{2}-v[0-9]{1,3}"))throw new IllegalArgumentException("Invalid bounded release label");
        tracer=telemetry.getTracer("fraud.lifecycle");this.meters=meters;this.costs=costs;this.release=release;
        for(var queue:Queue.values()) {var value=new AtomicLong();queues.put(queue,value);Gauge.builder("fraud.outbox.pending",value,AtomicLong::doubleValue).tag("queue",queue.name().toLowerCase(Locale.ROOT)).register(meters);}
    }
    @Override public <T>T observe(Stage stage,Supplier<T> operation) { return run(null,stage,operation); }
    @Override public <T>T continueTrace(String parent,Stage stage,Supplier<T> operation) { return run(parent,stage,operation); }
    private <T>T run(String parent,Stage stage,Supplier<T> operation) {
        var builder=tracer.spanBuilder("fraud."+stage.name().toLowerCase(Locale.ROOT));
        if(parent!=null&&!parent.isBlank()) {
            if(!parent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"))throw new IllegalArgumentException("Invalid trace parent");
            var fields=parent.split("-");
            var context=SpanContext.createFromRemoteParent(fields[1],fields[2],"01".equals(fields[3])?TraceFlags.getSampled():TraceFlags.getDefault(),TraceState.getDefault());
            if(!context.isValid())throw new IllegalArgumentException("Invalid trace identifiers");
            var incoming=Span.current().getSpanContext();
            if(incoming.isValid()&&!incoming.getTraceId().equals(context.getTraceId()))builder.addLink(incoming);
            builder.setParent(Context.root().with(Span.wrap(context)));
        }
        var span=builder.startSpan();span.setAttribute("fraud.release",release);
        long started=System.nanoTime();String outcome="success";
        try(var ignored=span.makeCurrent()) {return operation.get();}
        catch(RuntimeException|Error failure) {outcome="error";span.setStatus(StatusCode.ERROR,"Operation failed");throw failure;}
        finally {
            span.setAttribute("fraud.outcome",outcome);span.end();
            meters.counter("fraud.operation","stage",stage.name().toLowerCase(Locale.ROOT),"outcome",outcome,"release",release).increment();
            io.micrometer.core.instrument.Timer.builder("fraud.operation.duration").tags("stage",stage.name().toLowerCase(Locale.ROOT),"outcome",outcome,"release",release).publishPercentileHistogram().register(meters).record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
    @Override public void version(Version kind,String value) {
        String safe="unknown";
        if(value!=null&&value.length()<=300) {
            if(kind==Version.PROMPT&&(value.matches("investigator-v[1-9][0-9]*@[0-9a-f]{64}")||value.equals("week28-investigator-v1")))safe=value;
            if(kind==Version.MODEL&&value.matches("ollama:(qwen2\\.5:1\\.5b|embeddinggemma(:latest)?|gemma3:4b)@[0-9a-f]{64}"))safe=value;
            if(kind==Version.RETRIEVAL&&value.split(":",2)[0].matches("week[0-9]{2}(-scoped)?-v[1-9][0-9]*"))safe=value.split(":",2)[0];
        }
        Span.current().setAttribute("fraud."+kind.name().toLowerCase(Locale.ROOT)+".version",safe);
    }
    @Override public String parent() {var context=Span.current().getSpanContext();return context.isValid()?"00-"+context.getTraceId()+"-"+context.getSpanId()+"-"+(context.isSampled()?"01":"00"):null;}
    @Override public String traceId() {var context=Span.current().getSpanContext();return context.isValid()?context.getTraceId():null;}
    @Override public void backlog(Queue queue,long value) {queues.get(queue).set(Math.max(0,value));}
    @Override public void usage(long input,long output,boolean reported,long nanos) {costs.usage(input,output,reported,nanos);}
}
