/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/eval/EvalGate.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Reject semantic releases below fixed thresholds and reject unsafe trace outcomes.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 30
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Fixtures validate plumbing only; they cannot be presented as semantic quality evidence.
 *   - Thresholds are fixed before measurement and are never lowered to hide a failed experiment.
 * ============================================================================
 */
package com.fraudengine.platform.eval;

import java.util.*;

public class EvalGate {
    public List<String> failures(EvalService.Report report) {
        List<String> failures=new ArrayList<>();
        if(java.util.stream.DoubleStream.of(report.recallAt5(),report.mrr(),report.citationValidity(),
                report.supportedAnswerPrecision(),report.unknownAbstention(),report.answerRelevance())
                .anyMatch(value->!Double.isFinite(value)||value<0||value>1))
            failures.add("Invalid metric value");
        if(report.degraded())failures.add("Semantic gate rejects degraded reads");
        if(report.fixture())failures.add("Semantic gate requires actual embeddings");
        if(report.recallAt5()<0.95)failures.add("recall@5 below 0.95");
        if(report.mrr()<0.90)failures.add("MRR below 0.90");
        if(report.citationValidity()!=1)failures.add("Invalid citations");
        if(report.supportedAnswerPrecision()!=1)failures.add("Unsupported or absent quotations");
        if(report.unknownAbstention()!=1)failures.add("Unknown answer did not abstain");
        if(report.answerRelevance()<0.90)failures.add("Relevant supported answers below 0.90");
        if(report.scopeLeaks()!=0||report.unsafeExecutions()!=0)failures.add("Security invariant violated");
        return List.copyOf(failures);
    }
    public void requirePass(EvalService.Report report) {
        var failures=failures(report);
        if(!failures.isEmpty())throw new IllegalStateException(String.join("; ",failures));
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Expected a measured report path");
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var tree=mapper.readTree(java.nio.file.Path.of(args[0]).toFile());
        var report=mapper.treeToValue(tree.has("report")?tree.path("report"):tree,EvalService.Report.class);
        var failures=new EvalGate().failures(report);
        System.out.println(mapper.writeValueAsString(Map.of("passed",failures.isEmpty(),"failures",failures)));
        if(!failures.isEmpty())System.exit(1);
    }
}
