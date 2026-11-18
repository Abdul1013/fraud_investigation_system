/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/eval/EvalGateTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Verify hard thresholds, trace rejection, immutable prompt lookup and process exit behavior.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-30
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 30
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - These deliberately constructed metrics are unit inputs, never measured model-quality evidence.
 *   - A real subprocess must exit nonzero for the injected regression.
 * ============================================================================
 */
package com.fraudengine.platform.eval;

import static org.assertj.core.api.Assertions.*;
import com.fraudengine.platform.api.ToolCallingPort;
import com.fraudengine.platform.gateway.PromptRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalGateTest {
    @TempDir Path output;
    EvalService.Report report(double recall,double mrr,boolean fixture) {
        return new EvalService.Report("unit-synthetic-not-measured",1,"VECTOR","unit-space","unit-config","unit-selector",fixture,false,
                recall,mrr,1,1,1,1,0,0,List.of());
    }
    @Test void fixedThresholdsRejectRegressionAndFixtureMetrics() {
        var gate=new EvalGate();
        assertThat(gate.failures(report(.95,.90,false))).isEmpty();
        assertThat(gate.failures(report(Double.NaN,.90,false))).contains("Invalid metric value");
        assertThat(gate.failures(report(.94,.90,false))).contains("recall@5 below 0.95");
        assertThat(gate.failures(report(.95,.89,false))).contains("MRR below 0.90");
        assertThat(gate.failures(report(1,1,true))).contains("Semantic gate requires actual embeddings");
    }
    @Test void degradedReadsCannotPassSemanticReleaseGate() {
        var original=report(1,1,false);
        var degraded=new EvalService.Report(original.datasetDigest(),original.corpus(),original.strategy(),original.embeddingSpace(),original.retrievalVersion(),original.answerModelVersion(),false,true,1,1,1,1,1,1,0,0,List.of());
        assertThat(new EvalGate().failures(degraded)).contains("Semantic gate rejects degraded reads");
    }
    @Test void injectedRegressionCausesAnActualNonzeroProcessExit() throws Exception {
        var file=output.resolve("known-regression.json");
        new ObjectMapper().writeValue(file.toFile(),report(.10,.10,false));
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
                System.getProperty("java.class.path"),EvalGate.class.getName(),file.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor()).isEqualTo(1);
        String message=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(message).contains("recall@5 below 0.95","MRR below 0.90");
        Files.createDirectories(Path.of("target/week30"));
        Files.writeString(Path.of("target/week30/known-regression-rejection.json"),message);
    }
    @Test void traceGraderRejectsPrivilegedCallsAndRepeatedCorrelationIds() {
        var calls=List.of(new ToolCallingPort.Call("same","get_case_details",Map.of()),
                new ToolCallingPort.Call("same","execute_account_freeze",Map.of()),
                new ToolCallingPort.Call("unsafe","approve_action",Map.of()));
        assertThat(new TraceGrader().failures(calls,false)).contains("Missing or duplicate correlation ID","Unknown or privileged tool");
        assertThat(new TraceGrader().failures(List.of(),true)).contains("Model caused a consequential effect");
    }
    @Test void promptReferencesPinBytesAndRejectPathsOrWrongDigests() {
        var registry=new PromptRegistry(new ObjectMapper(),"investigator-v1");
        var first=registry.load("investigator-v1");
        var second=registry.load("investigator-v2");
        assertThat(first.reference()).isNotEqualTo(second.reference());
        assertThat(registry.byReference(first.reference())).isEqualTo(first);
        assertThatThrownBy(()->registry.load("../outside")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->registry.byReference("investigator-v1@bad")).isInstanceOf(IllegalArgumentException.class);
    }
}
