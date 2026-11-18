/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/eval/EvalService.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Score actual scoped retrieval and supported answers against labelled golden questions.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 30
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Quality measurements retain question-level failures; fixture reports cannot satisfy a semantic quality gate.
 *   - Grounding is exact source support, not proof of relevance; relevance and unknown abstention are scored separately.
 *   - Cross-context execution uses only the public scoped KnowledgeApi.
 * ============================================================================
 */
package com.fraudengine.platform.eval;

import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EvalService {
    public record Expected(String sourceId, int sourceVersion, String contains) {
    }

    public record Question(String id, String query, boolean unknown, List<Expected> relevant) {
    }

    public record Row(String id, boolean unknown, double recall, double reciprocalRank, boolean answerRelevant,
            boolean abstained, int quotes, int supported, List<String> retrieved, double millis, boolean degraded) {
    }

    public record Report(String datasetDigest, int corpus, String strategy, String embeddingSpace,
            String retrievalVersion, String answerModelVersion, boolean fixture, boolean degraded,
            double recallAt5, double mrr, double citationValidity, double supportedAnswerPrecision,
            double unknownAbstention, double answerRelevance, int scopeLeaks, int unsafeExecutions,
            List<Row> questions) {
    }

    private final KnowledgeApi knowledge;
    private final com.fraudengine.cases.api.CaseApi cases;
    private final com.fraudengine.audit.api.AuditApi audit;

    public EvalService(KnowledgeApi knowledge, com.fraudengine.cases.api.CaseApi cases,
            com.fraudengine.audit.api.AuditApi audit) {
        this.knowledge = knowledge;
        this.cases = cases;
        this.audit = audit;
    }

    public Report evaluate(String caseId, List<Question> questions, String datasetDigest, int corpus,
            RetrievalRequest.Strategy strategy) {
        if (questions.size() < 30 || questions.stream().map(Question::id).distinct().count() != questions.size()
                || questions.stream().filter(q -> !q.unknown()).count() < 20
                || questions.stream().filter(Question::unknown).count() < 2)
            throw new IllegalArgumentException(
                    "Golden dataset requires unique IDs, 30 questions, and known/unknown coverage");
        var scope = cases.evidenceScope(caseId);
        long effectsBefore = effects(caseId);
        int scopeLeaks = 0;
        List<Row> rows = new ArrayList<>();
        String space = null, config = null, answerModel = null;
        boolean fixture = false, degraded = false;
        int invalidCitations = 0, quotes = 0, supported = 0;
        for (var question : questions) {
            if (question.id() == null || question.query() == null || question.relevant() == null
                    || (!question.unknown() && question.relevant().isEmpty()))
                throw new IllegalArgumentException("Missing labels");
            long start = System.nanoTime();
            var answer = knowledge.answerCaseEvidence(caseId,
                    new RetrievalRequest(question.query(), corpus, strategy, 5, null, null));
            var result = answer.retrieval();
            degraded |= result.degraded();
            if (space != null && !space.equals(result.embeddingSpace()))
                throw new IllegalStateException("Embedding space changed during evaluation");
            space = result.embeddingSpace();
            fixture = result.fixtureEmbeddings();
            if (config != null && !config.equals(result.retrievalConfigVersion()))
                throw new IllegalStateException("Retrieval config changed during evaluation");
            config = result.retrievalConfigVersion();
            if (!"not-called".equals(answer.answerModelVersion())) {
                if (answerModel != null && !answerModel.equals(answer.answerModelVersion()))
                    throw new IllegalStateException("Answer selector changed during evaluation");
                answerModel = answer.answerModelVersion();
            }
            scopeLeaks += (int) result.passages().stream()
                    .filter(p -> !scope.sourceIds().contains(p.citation().sourceId())
                            || p.citation().classification().compareTo(scope.classificationCeiling()) > 0
                            || "C3".equals(p.citation().classification()))
                    .count();
            int matches = 0;
            double rr = 0;
            for (var expected : question.relevant())
                if (result.passages().stream().anyMatch(p -> matches(expected, p)))
                    matches++;
            for (int rank = 0; rank < result.passages().size(); rank++) {
                var passage = result.passages().get(rank);
                if (question.relevant().stream().anyMatch(expected -> matches(expected, passage))) {
                    rr = 1.0 / (rank + 1);
                    break;
                }
            }
            boolean relevant = answer.evidence().stream()
                    .anyMatch(quote -> question.relevant().stream()
                            .anyMatch(expected -> expected.sourceId().equals(quote.citation().sourceId())
                                    && expected.sourceVersion() == quote.citation().sourceVersion()
                                    && quote.quote().contains(expected.contains())));
            int supportedHere = 0;
            for (var quote : answer.evidence()) {
                quotes++;
                var passage = result.passages().stream().filter(p -> p.citation().equals(quote.citation())).findFirst();
                if (passage.isEmpty())
                    invalidCitations++;
                else if (passage.get().text().contains(quote.quote())) {
                    supportedHere++;
                    supported++;
                }
            }
            rows.add(new Row(question.id(), question.unknown(),
                    question.relevant().isEmpty() ? 0 : matches / (double) question.relevant().size(),
                    rr, relevant, "INSUFFICIENT_EVIDENCE".equals(answer.status()), answer.evidence().size(),
                    supportedHere,
                    result.passages().stream()
                            .map(p -> p.citation().sourceId() + "@" + p.citation().sourceVersion() + "#"
                                    + p.citation().chunkId())
                            .toList(),
                    (System.nanoTime() - start) / 1_000_000.0, result.degraded()));
        }
        var known = rows.stream().filter(r -> !r.unknown()).toList();
        var unknown = rows.stream().filter(Row::unknown).toList();
        return new Report(datasetDigest, corpus, strategy.name(), space, config, answerModel, fixture, degraded,
                known.stream().mapToDouble(Row::recall).average().orElse(0),
                known.stream().mapToDouble(Row::reciprocalRank).average().orElse(0),
                quotes == 0 ? 0 : 1 - invalidCitations / (double) quotes, quotes == 0 ? 0 : supported / (double) quotes,
                unknown.stream().filter(Row::abstained).count() / (double) unknown.size(),
                known.stream().filter(Row::answerRelevant).count() / (double) known.size(), scopeLeaks,
                (int) (effects(caseId) - effectsBefore), List.copyOf(rows));
    }

    private long effects(String caseId) {
        long count = 0, cursor = 0;
        while (true) {
            var page = audit.history(caseId, cursor, 200);
            count += page.stream().filter(event -> "SIMULATED_FREEZE_EXECUTED".equals(event.type())).count();
            if (page.isEmpty())
                return count;
            cursor = page.getLast().sequence();
        }
    }

    private boolean matches(Expected expected, RetrievalResult.Passage passage) {
        return expected.sourceId().equals(passage.citation().sourceId())
                && expected.sourceVersion() == passage.citation().sourceVersion()
                && passage.text().contains(expected.contains());
    }
}
