/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/infrastructure/RetrievalController.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Bound global curator reads at the authenticated HTTP adapter.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-27
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Internal retrieval remains usable for offline evaluation; scoped public APIs enforce their own admission.
 *   - Indexing has an independent non-queued semaphore and retains curator authorization.
 * ============================================================================
 */
package com.fraudengine.knowledge.infrastructure;

import com.fraudengine.knowledge.api.dto.*;
import com.fraudengine.knowledge.retrieval.*;
import com.fraudengine.platform.gateway.ModelUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class RetrievalController {
    private final com.fraudengine.platform.api.ReadAdmission admission;
    private final RetrievalService retrieval;
    private final AnswerService answers;
    private final RetrievalIndexService index;

    public RetrievalController(RetrievalService retrieval, AnswerService answers, RetrievalIndexService index,
            com.fraudengine.platform.api.ReadAdmission admission) {
        this.admission = admission;
        this.retrieval = retrieval;
        this.answers = answers;
        this.index = index;
    }

    public record IndexRequest(@Positive Integer corpusVersion) {
    }

    @PostMapping({ "/v1/retrieve", "/v1/knowledge/retrieval" })
    public RetrievalResult retrieve(@Valid @RequestBody RetrievalRequest request) {
        return admission.call(() -> retrieval.retrieve(request));
    }

    @PostMapping("/v1/knowledge/answers")
    public AnswerResult answer(@Valid @RequestBody RetrievalRequest request) {
        return admission.call(() -> answers.answer(request));
    }

    @PostMapping("/v1/knowledge/retrieval/index")
    public RetrievalIndexService.IndexReport index(@Valid @RequestBody IndexRequest request) {
        return index.index(request.corpusVersion());
    }

    @ExceptionHandler({ CorpusUnavailableException.class, IndexUnavailableException.class })
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
