/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/observability/OperationalDemoTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Measure actual local usage and prove a real monitoring alert fires and recovers.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-32
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - ops.demo=true requires the local Compose monitoring stack and installed Ollama models.
 *   - Provider failure/latency uses an explicit protocol fixture; native usage calls the actual local model.
 *   - Only sanitized metadata, counters and alert state transitions are written to evidence.
 * ============================================================================
 */
package com.fraudengine.platform.observability;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fraudengine.platform.api.*;
import com.fraudengine.platform.gateway.provider.*;
import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.*;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named="ops.demo",matches="true")
class OperationalDemoTest {
    final ObjectMapper mapper=new ObjectMapper();
    final HttpClient http=HttpClient.newHttpClient();
    void write(String name,Object value) throws Exception {
        Path path=Path.of("docs/evidence/week32",name);Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),Map.of("_file",path.toString(),"_purpose","Actual local operational demonstration; no credentials or source bodies", "report",value));
    }
    JsonNode alerts() throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:9090/api/v1/alerts")).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);return mapper.readTree(response.body()).path("data").path("alerts");
    }
    boolean firing(JsonNode alerts) {
        for(var alert:alerts)if("FraudProviderErrors".equals(alert.path("labels").path("alertname").asText())&&"firing".equals(alert.path("state").asText()))return true;return false;
    }
    @Test void actualNativeUsageAndProtocolFailureAlertAreMeasuredSeparately() throws Exception {
        var config=new OtlpConfig() {public String get(String key){return null;} public String url(){return "http://127.0.0.1:4318/v1/metrics";}public Duration step(){return Duration.ofSeconds(5);}public Map<String,String> resourceAttributes(){return Map.of("service.name","fraud-operations-demo");}};
        var meters=new OtlpMeterRegistry(config,Clock.SYSTEM);
        try(var provider=SdkTracerProvider.builder().setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"),"fraud-operations-demo")))
                .addSpanProcessor(SimpleSpanProcessor.create(OtlpHttpSpanExporter.builder().setEndpoint("http://127.0.0.1:4318/v1/traces").build())).build()) {
            var telemetry=new LifecycleTelemetry(OpenTelemetrySdk.builder().setTracerProvider(provider).build(),meters,new CostMetrics(meters,.08),"week32-v1");
            var nativeClient=new OllamaLocalClient("http://127.0.0.1:11434",mapper,telemetry);
            var nativeModel=new OllamaToolCallingAdapter(nativeClient,"qwen2.5:1.5b",mapper);
            var answer=nativeModel.turn(List.of(new ToolCallingPort.Message("user","Reply with the word ready.",null,null)),List.of());
            assertThat(answer.usage().reported()).isTrue();assertThat(answer.usage().inputTokens()).isPositive();
            write("native-usage.json",Map.of("kind","actual local Ollama, synthetic prompt", "model",answer.modelVersion(),"usage",answer.usage(),"localComputeUsdPerHourAssumption",.08,
                    "estimatedLocalComputeUsd",meters.get("fraud.local.compute.estimated.usd").counter().count(),"apiChargedUsd",0));
            var failing=new AtomicBoolean(true);var fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            fixture.createContext("/api/chat",exchange->{try {Thread.sleep(1200);}catch(InterruptedException error){Thread.currentThread().interrupt();}byte[] body="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(failing.get()?503:200,body.length);exchange.getResponseBody().write(body);exchange.close();});fixture.start();
            List<Map<String,Object>> timeline=new ArrayList<>();
            try {
                var broken=new OllamaLocalClient("http://127.0.0.1:"+fixture.getAddress().getPort(),mapper,telemetry);
                boolean observed=false;long deadline=System.nanoTime()+Duration.ofSeconds(100).toNanos();
                while(System.nanoTime()<deadline) {
                    assertThatThrownBy(()->broken.post("/api/chat",Map.of("content","synthetic operational probe"))).isInstanceOf(RuntimeException.class);
                    JsonNode state=alerts();timeline.add(Map.of("at",Instant.now().toString(),"phase","forced-503-and-1.2s-delay","providerAlertFiring",firing(state)));
                    if(firing(state)){observed=true;break;}Thread.sleep(1000);
                }
                assertThat(observed).as("actual Prometheus provider alert firing").isTrue();
                failing.set(false);broken.post("/api/chat",Map.of("content","synthetic recovered probe"));
                boolean recovered=false;deadline=System.nanoTime()+Duration.ofSeconds(100).toNanos();
                while(System.nanoTime()<deadline) {
                    JsonNode state=alerts();timeline.add(Map.of("at",Instant.now().toString(),"phase","provider-restored","providerAlertFiring",firing(state)));
                    if(!firing(state)){recovered=true;break;}Thread.sleep(2000);
                }
                write("alert-timeline.json",Map.of("fixture","real HTTP 503/slow response fixture; not a native-model outage","fired",observed,"recovered",recovered,"timeline",timeline));
                assertThat(recovered).isTrue();
            }finally {fixture.stop(0);}
        }finally {meters.close();}
    }
}
