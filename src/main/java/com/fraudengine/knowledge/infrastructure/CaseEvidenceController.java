/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/infrastructure/CaseEvidenceController.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Exposes a case-scoped evidence endpoint through the public knowledge API.
 * ----------------------------------------------------------------------------
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Identity and source grants are absent from the body; the service resolves both on the server.
 *   - Corpus and model failures retain the same status as the legacy retrieval adapter.
 * ============================================================================
 */
package com.fraudengine.knowledge.infrastructure;

import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.RetrievalRequest;
import com.fraudengine.knowledge.api.dto.RetrievalResult;
import com.fraudengine.knowledge.retrieval.CorpusUnavailableException;
import com.fraudengine.knowledge.retrieval.IndexUnavailableException;
import com.fraudengine.platform.gateway.ModelUnavailableException;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CaseEvidenceController {
    private final KnowledgeApi knowledge;

    public CaseEvidenceController(KnowledgeApi knowledge) {
        this.knowledge = knowledge;
    }

    @PostMapping("/v1/cases/{caseId}/evidence")
    public RetrievalResult evidence(@PathVariable String caseId, @Valid @RequestBody RetrievalRequest request) {
        return knowledge.retrieveCaseEvidence(caseId, request);
    }

    @ExceptionHandler({CorpusUnavailableException.class, IndexUnavailableException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> unavailable(RuntimeException error) {
        return Map.of("error", error.getMessage());
    }

    @ExceptionHandler(ModelUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> provider(RuntimeException error) {
        return Map.of("error", error.getMessage());
    }
}
