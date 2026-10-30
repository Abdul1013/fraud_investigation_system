/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/retrieval/AnswerService.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Ground answers in verified quotations and explicitly label scoped degraded reads.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-27
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Public HTTP and scoped KnowledgeApi adapters own admission; internal evaluation is a trusted offline workload.
 *   - Provider failures permit scoped extractive reading only, never proposals or execution.
 * ============================================================================
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
    public AnswerService(RetrievalService retrieval,EvidenceAnswerPort model) {this.retrieval=retrieval;this.model=model;}
    public AnswerResult answer(RetrievalRequest request) {return answerRetrieved(request,retrieval.retrieve(request));}

    public AnswerResult answerRetrieved(RetrievalRequest request,RetrievalResult found) {return answerRetrieved(request,found,false);}
    public AnswerResult answerRetrieved(RetrievalRequest request,RetrievalResult found,boolean allowDegraded) {
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
        String version;
        List<EvidenceAnswerPort.ProposedQuote> proposed;
        if(found.degraded()) {
            version="degraded-extractive-v1";proposed=extractive(context);
        } else {
            try {version=model.modelVersion();proposed=model.select(request.query(),List.copyOf(context));}
            catch(com.fraudengine.platform.gateway.ModelUnavailableException unavailable) {
                if(!allowDegraded)throw unavailable;
                found=found.asDegraded("ANSWER_PROVIDER_UNAVAILABLE");version="degraded-extractive-v1";proposed=extractive(context);
            }
        }
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
                found.degraded()?"DEGRADED_READ":"ANSWERED", found.degraded()?"Degraded read (verified quotations only):\n"+text:text, List.copyOf(quotes), found, version, "exact-quotes-v1");
    }

    private List<EvidenceAnswerPort.ProposedQuote> extractive(List<EvidenceAnswerPort.Evidence> evidence) {
        return evidence.stream().filter(item->item.text().length()>=8&&item.text().length()<=4000).limit(3)
            .map(item->new EvidenceAnswerPort.ProposedQuote(item.chunkId(),item.text())).toList();
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
