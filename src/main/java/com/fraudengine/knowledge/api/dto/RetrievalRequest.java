/**
 * WEEK 27 — Public retrieval inputs. Jakarta validation bounds query size and result count before
 * SQL/model work. A nullable corpus version selects the latest published snapshot; responses always
 * identify the resolved version. The caller can filter document type and effective date, but cannot
 * assign their own classification clearance. VECTOR and HYBRID are explicit strategies so the same
 * request can be evaluated fairly against both. VECTOR is the default because the measured local
 * comparison ranked correct passages slightly better and is the simpler path; this is a
 * dataset-specific choice, not a claim that hybrid is always inferior.
 */
package com.fraudengine.knowledge.api.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RetrievalRequest(
        @NotBlank @Size(max = 2000) String query,
        @Positive Integer corpusVersion,
        Strategy strategy,
        @Min(1) @Max(20) Integer topK,
        @Pattern(regexp = "policy|prior_case|regulatory") String docType,
        LocalDate asOf) {
    public enum Strategy {
        VECTOR,
        HYBRID
    }

    public Strategy selectedStrategy() {
        return strategy == null ? Strategy.VECTOR : strategy;
    }

    public int limit() {
        return topK == null ? 5 : topK;
    }
}
