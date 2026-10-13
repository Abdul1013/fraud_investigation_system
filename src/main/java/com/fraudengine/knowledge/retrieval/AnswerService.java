/**
 * Deterministic grounding gate around evidence selection. The service hashes retrieved
 * text against lineage, supplies a bounded context, and accepts only quotations that occur exactly
 * in an allowed passage. Unknown chunk IDs, invented text, malformed claims, and empty support
 * produce explicit abstention. An LLM cannot generate citation metadata or execute tools here. This
 * proves quote support, not relevance or completeness; those are evaluated separately against
 * golden questions.
 */
package com.fraudengine.knowledge.retrieval;

import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader;
import com.fraudengine.platform.gateway.EvidenceAnswerPort;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnswerService {
    private final RetrievalService retrieval;
    private final EvidenceAnswerPort model;

    public AnswerService(RetrievalService retrieval, EvidenceAnswerPort model) {
        this.retrieval = retrieval;
        this.model = model;
    }

    public AnswerResult answer(RetrievalRequest request) {
        var found = retrieval.retrieve(request);
        if (found.passages().isEmpty())
            return abstain(found, "INSUFFICIENT_EVIDENCE", "not-called");
        Map<String, RetrievalResult.Passage> allowed = new LinkedHashMap<>();
        List<EvidenceAnswerPort.Evidence> context = new ArrayList<>();
        int remaining = 16000;
        for (var passage : found.passages()) {
            if (!SourceDocumentLoader.sha256(passage.text()).equals(passage.citation().chunkHash()))
                return abstain(found, "INVALID_EVIDENCE", "not-called");
            if (passage.text().length() > remaining)
                continue;
            String id = passage.citation().chunkId().toString();
            allowed.put(id, passage);
            context.add(new EvidenceAnswerPort.Evidence(id, passage.text()));
            remaining -= passage.text().length();
        }
        if (context.isEmpty())
            return abstain(found, "INSUFFICIENT_EVIDENCE", "not-called");
        String version = model.modelVersion();
        var proposed = model.select(request.query(), List.copyOf(context));
        if (proposed == null || proposed.size() > 3)
            return abstain(found, "INVALID_MODEL_OUTPUT", version);
        List<AnswerResult.SupportedQuote> quotes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var claim : proposed) {
            if (claim == null || claim.chunkId() == null || claim.quote() == null)
                return abstain(found, "INVALID_MODEL_OUTPUT", version);
            var passage = allowed.get(claim.chunkId());
            if (passage == null
                    || claim.quote().length() < 8
                    || claim.quote().length() > 4000
                    || !passage.text().contains(claim.quote()))
                return abstain(found, "INVALID_MODEL_OUTPUT", version);
            if (seen.add(claim.chunkId() + ":" + claim.quote()))
                quotes.add(new AnswerResult.SupportedQuote(claim.quote(), passage.citation()));
        }
        if (quotes.isEmpty())
            return abstain(found, "INSUFFICIENT_EVIDENCE", version);
        String text = quotes.stream()
                .map(
                        q -> q.quote()
                                + " ["
                                + q.citation().sourceId()
                                + "@"
                                + q.citation().sourceVersion()
                                + "]")
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return new AnswerResult(
                "ANSWERED", text, List.copyOf(quotes), found, version, "exact-quotes-v1");
    }

    private AnswerResult abstain(RetrievalResult found, String status, String modelVersion) {
        return new AnswerResult(
                status,
                "I do not have sufficient verified evidence to answer this question.",
                List.of(),
                found,
                modelVersion,
                "exact-quotes-v1");
    }
}
