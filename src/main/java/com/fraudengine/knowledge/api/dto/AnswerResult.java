/**
 * WEEK 27 — An answer contains only exact source quotes with validated citations, or an explicit
 * abstention. This deliberately starts with extractive RAG: substring checks can prove that a
 * quoted fact exists in a passage. We do not claim that valid citation IDs prove arbitrary
 * paraphrases or that a retrieved policy is legally authoritative.
 */
package com.fraudengine.knowledge.api.dto;

import java.util.List;

public record AnswerResult(
        String status,
        String answer,
        List<SupportedQuote> evidence,
        RetrievalResult retrieval,
        String answerModelVersion,
        String promptVersion) {
    public record SupportedQuote(String quote, Citation citation) {}
}
