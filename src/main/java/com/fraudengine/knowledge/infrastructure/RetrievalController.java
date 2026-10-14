/**
 *  Thin HTTP adapter exposes retrieval, grounded answers, and explicit model indexing.
 * Validation occurs before service work. Missing corpus/index returns 409; unavailable model returns 503;
 * a legitimate unknown answer remains a successful response with INSUFFICIENT_EVIDENCE. Separating these
 * cases prevents a broken environment from masquerading as an empty knowledge base. Development identity
 * clearance is server configuration; full authenticated per-user authorization remains Week 31 work.
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
    private final RetrievalService retrieval;
    private final AnswerService answers;
    private final RetrievalIndexService index;

    public RetrievalController(RetrievalService retrieval, AnswerService answers, RetrievalIndexService index) {
        this.retrieval = retrieval;
        this.answers = answers;
        this.index = index;
    }

    public record IndexRequest(@Positive Integer corpusVersion) {
    }

    @PostMapping({ "/v1/retrieve", "/v1/knowledge/retrieval" })
    public RetrievalResult retrieve(@Valid @RequestBody RetrievalRequest request) {
        return retrieval.retrieve(request);
    }

    @PostMapping("/v1/knowledge/answers")
    public AnswerResult answer(@Valid @RequestBody RetrievalRequest request) {
        return answers.answer(request);
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
