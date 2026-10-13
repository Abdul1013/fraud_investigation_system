/**
 * Repeatable golden-dataset comparison of VECTOR and HYBRID on one fixed corpus and k.
 * Labels identify source revision AND evidence section, so finding the right document but wrong
 * passage does not count as success. We measure retrieval recall/precision/MRR, answer relevance,
 * exact quote support, unknown abstention, and observed latency separately. Reports include dataset
 * digest and model/version identifiers. Failure rows stay visible; no tuning result is fabricated
 * and no LLM judge marks its own work.
 */
package com.fraudengine.knowledge.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.knowledge.api.dto.*;

import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Component
public class RetrievalEvaluator {
        public record Expected(String sourceId, int sourceVersion, String contains) {
        }

        public record Question(String id, String query, boolean unknown, List<Expected> relevant) {
        }

        public record Row(
                        String id,
                        String query,
                        boolean unknown,
                        List<Expected> expected,
                        List<String> retrieved,
                        double recallAtK,
                        double precisionAtK,
                        double reciprocalRank,
                        boolean answerRelevant,
                        String answerStatus,
                        int quoteCount,
                        int supportedQuotes,
                        double latencyMillis) {
        }

        public record StrategyReport(
                        String strategy,
                        double recallAtK,
                        double precisionAtK,
                        double mrr,
                        double answerRelevance,
                        double quoteSupportRate,
                        double unknownAbstentionRate,
                        double meanLatencyMillis,
                        List<Row> questions) {
        }

        public record Report(
                        String datasetSha256,
                        int corpusVersion,
                        int topK,
                        String embeddingSpace,
                        boolean fixtureEmbeddings,
                        String retrievalConfigVersion,
                        String answerModelVersion,
                        String promptVersion,
                        Instant generatedAt,
                        List<StrategyReport> strategies) {
        }

        private final RetrievalService retrieval;
        private final AnswerService answers;
        private final ObjectMapper mapper;

        public RetrievalEvaluator(
                        RetrievalService retrieval, AnswerService answers, ObjectMapper mapper) {
                this.retrieval = retrieval;
                this.answers = answers;
                this.mapper = mapper;
        }

        public Report evaluate(Path dataset, int corpus, int topK, Path output)
                        throws java.io.IOException {
                String raw = Files.readString(dataset);
                var datasetNode = mapper.readTree(raw);
                var questions = mapper.treeToValue(
                                datasetNode.isArray() ? datasetNode : datasetNode.path("questions"),
                                Question[].class);
                if (questions.length == 0
                                || Arrays.stream(questions)
                                                .anyMatch(
                                                                q -> q.id() == null
                                                                                || q.query() == null
                                                                                || q.relevant() == null
                                                                                || (!q.unknown() && q.relevant()
                                                                                                .isEmpty())))
                        throw new IllegalArgumentException(
                                        "Golden questions require IDs, queries, and expected evidence");
                List<StrategyReport> strategies = new ArrayList<>();
                String space = null, model = null, prompt = null, config = null;
                boolean fixture = false;
                for (var strategy : RetrievalRequest.Strategy.values()) {
                        // Exclude model loading from the scored serial observations; this is still not
                        // a load
                        // benchmark.
                        answers.answer(
                                        new RetrievalRequest(questions[0].query(), corpus, strategy, topK, null, null));
                        List<Row> rows = new ArrayList<>();
                        for (var question : questions) {
                                var request = new RetrievalRequest(question.query(), corpus, strategy, topK, null,
                                                null);
                                long start = System.nanoTime();
                                var answer = answers.answer(request);
                                double latency = (System.nanoTime() - start) / 1_000_000.0;
                                var found = answer.retrieval();
                                if (space != null && !space.equals(found.embeddingSpace()))
                                        throw new IllegalStateException("Model space changed during comparison");
                                space = found.embeddingSpace();
                                config = found.retrievalConfigVersion();
                                fixture = found.fixtureEmbeddings();
                                if (!answer.answerModelVersion().equals("not-called"))
                                        model = answer.answerModelVersion();
                                prompt = answer.promptVersion();
                                int matched = 0;
                                for (var expected : question.relevant())
                                        if (found.passages().stream().anyMatch(p -> matches(expected, p)))
                                                matched++;
                                int relevantReturned = 0;
                                double rr = 0;
                                for (int rank = 0; rank < found.passages().size(); rank++) {
                                        var p = found.passages().get(rank);
                                        if (question.relevant().stream().anyMatch(e -> matches(e, p))) {
                                                relevantReturned++;
                                                if (rr == 0)
                                                        rr = 1.0 / (rank + 1);
                                        }
                                }
                                int supported = (int) answer.evidence().stream()
                                                .filter(
                                                                q -> found.passages().stream()
                                                                                .anyMatch(
                                                                                                p -> p.citation()
                                                                                                                .equals(
                                                                                                                                q
                                                                                                                                                .citation())
                                                                                                                && p.text()
                                                                                                                                .contains(
                                                                                                                                                q
                                                                                                                                                                .quote())))
                                                .count();
                                boolean answerRelevant = false;
                                for (var quote : answer.evidence()) {
                                        for (var passage : found.passages()) {
                                                if (passage.citation().equals(quote.citation())
                                                                && question.relevant().stream()
                                                                                .anyMatch(label -> matches(label,
                                                                                                passage)))
                                                        answerRelevant = true;
                                        }
                                }
                                rows.add(
                                                new Row(
                                                                question.id(),
                                                                question.query(),
                                                                question.unknown(),
                                                                question.relevant(),
                                                                found.passages().stream()
                                                                                .map(
                                                                                                p -> p.citation()
                                                                                                                .sourceId()
                                                                                                                + "@"
                                                                                                                + p.citation().sourceVersion()
                                                                                                                + "#"
                                                                                                                + p.citation().chunkId())
                                                                                .toList(),
                                                                question.relevant().isEmpty()
                                                                                ? 0
                                                                                : matched / (double) question.relevant()
                                                                                                .size(),
                                                                found.passages().isEmpty()
                                                                                ? 0
                                                                                : relevantReturned / (double) found
                                                                                                .passages().size(),
                                                                rr,
                                                                answerRelevant,
                                                                answer.status(),
                                                                answer.evidence().size(),
                                                                supported,
                                                                latency));
                        }
                        var known = rows.stream().filter(r -> !r.unknown()).toList();
                        var unknown = rows.stream().filter(Row::unknown).toList();
                        int quotes = rows.stream().mapToInt(Row::quoteCount).sum();
                        strategies.add(
                                        new StrategyReport(
                                                        strategy.name(),
                                                        known.stream().mapToDouble(Row::recallAtK).average().orElse(0),
                                                        known.stream().mapToDouble(Row::precisionAtK).average()
                                                                        .orElse(0),
                                                        known.stream().mapToDouble(Row::reciprocalRank).average()
                                                                        .orElse(0),
                                                        known.stream().filter(Row::answerRelevant).count()
                                                                        / (double) Math.max(1, known.size()),
                                                        quotes == 0
                                                                        ? 0
                                                                        : rows.stream().mapToInt(Row::supportedQuotes)
                                                                                        .sum()
                                                                                        / (double) quotes,
                                                        unknown.stream()
                                                                        .filter(
                                                                                        r -> r.answerStatus()
                                                                                                        .equals(
                                                                                                                        "INSUFFICIENT_EVIDENCE"))
                                                                        .count()
                                                                        / (double) Math.max(1, unknown.size()),
                                                        rows.stream().mapToDouble(Row::latencyMillis).average()
                                                                        .orElse(0),
                                                        List.copyOf(rows)));
                }
                var report = new Report(
                                com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader.sha256(raw),
                                corpus,
                                topK,
                                space,
                                fixture,
                                config,
                                model == null ? "not-called" : model,
                                prompt,
                                Instant.now(),
                                strategies);
                Files.createDirectories(output);
                mapper.writerWithDefaultPrettyPrinter()
                                .writeValue(output.resolve("retrieval-comparison.json").toFile(), report);
                StringBuilder markdown = new StringBuilder(
                                "# Retrieval comparison\n\nDataset SHA-256: `"
                                                + report.datasetSha256()
                                                + "`\n\nCorpus: "
                                                + corpus
                                                + ", k="
                                                + topK
                                                + ", embedding space: `"
                                                + space
                                                + "`, fixture="
                                                + fixture
                                                + "\n\n");
                markdown.append(
                                "| Strategy | Recall@k | Precision@k | MRR | Answer relevance | Quote support |"
                                                + " Unknown abstention | Mean ms |\n"
                                                + "|---|---:|---:|---:|---:|---:|---:|---:|\n");
                for (var s : strategies)
                        markdown.append(
                                        String.format(
                                                        Locale.ROOT,
                                                        "| %s | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.1f |%n",
                                                        s.strategy(),
                                                        s.recallAtK(),
                                                        s.precisionAtK(),
                                                        s.mrr(),
                                                        s.answerRelevance(),
                                                        s.quoteSupportRate(),
                                                        s.unknownAbstentionRate(),
                                                        s.meanLatencyMillis()));
                markdown.append(
                                "\n"
                                                + "Quote support means exact source substring plus valid citation, not semantic"
                                                + " relevance. Latencies are serial observations after warm-up, not load-test"
                                                + " percentiles. Fixtures cannot establish semantic quality.\n\n"
                                                + "## Failure examples\n\n");
                for (var s : strategies)
                        for (var row : s.questions())
                                if ((!row.unknown() && (!row.answerRelevant() || row.recallAtK() < 1))
                                                || (row.unknown()
                                                                && !row.answerStatus().equals("INSUFFICIENT_EVIDENCE")))
                                        markdown.append(
                                                        "- "
                                                                        + s.strategy()
                                                                        + " / "
                                                                        + row.id()
                                                                        + ": recall="
                                                                        + row.recallAtK()
                                                                        + ", status="
                                                                        + row.answerStatus()
                                                                        + ", answerRelevant="
                                                                        + row.answerRelevant()
                                                                        + ". Question: "
                                                                        + row.query()
                                                                        + "\n");
                Files.writeString(output.resolve("retrieval-comparison.md"), markdown.toString());
                return report;
        }

        private boolean matches(Expected expected, RetrievalResult.Passage passage) {
                return expected.sourceId().equals(passage.citation().sourceId())
                                && expected.sourceVersion() == passage.citation().sourceVersion()
                                && passage.text().contains(expected.contains());
        }
}
