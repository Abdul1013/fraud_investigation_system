/**
 * WEEK 27 — Deterministic extractive answer baseline, usable without a chat model. It selects whole
 * evidence sections with meaningful query overlap and never generates free-form factual claims.
 * This makes failures observable: lexical overlap misses paraphrases and can select weak evidence,
 * so the Week 27 report evaluates relevance separately from citation validity. The optional local
 * model can replace selection through the same narrow port without changing backend citation
 * checks.
 */
package com.fraudengine.platform.gateway.provider;

import com.fraudengine.platform.gateway.EvidenceAnswerPort;

import java.util.*;
import java.util.regex.*;

public class ExtractiveAnswerAdapter implements EvidenceAnswerPort {
    private static final Set<String> STOP =
            Set.of(
                    "what", "which", "when", "where", "why", "how", "is", "are", "the", "a", "an",
                    "of", "for", "to", "in", "and", "do", "does", "can", "be", "must", "should",
                    "with", "it", "on", "from", "before", "after", "at", "by", "as", "was", "were",
                    "would", "could");

    @Override
    public String modelVersion() {
        return "deterministic-extractive-v1";
    }

    private Set<String> terms(String text) {
        Set<String> terms = new HashSet<>();
        var matcher = Pattern.compile("[\\p{L}\\p{N}]+").matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) if (!STOP.contains(matcher.group())) terms.add(matcher.group());
        return terms;
    }

    @Override
    public List<ProposedQuote> select(String question, List<Evidence> evidence) {
        Set<String> wanted = terms(question);
        List<ProposedQuote> result = new ArrayList<>();
        for (var passage : evidence) {
            // Keep the entire evidence section, especially a table, rather than inventing a
            // summary.
            var overlap = terms(passage.text());
            overlap.retainAll(wanted);
            if (overlap.isEmpty() || passage.text().length() > 4000) continue;
            if (passage.text().strip().startsWith("# ") && !passage.text().contains("## "))
                continue;
            result.add(new ProposedQuote(passage.chunkId(), passage.text()));
            if (result.size() == 3) break;
        }
        return List.copyOf(result);
    }
}
