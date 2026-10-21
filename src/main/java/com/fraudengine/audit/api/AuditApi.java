/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/audit/api/AuditApi.java
 * CONTEXT:     audit
 * LAYER:       api
 * PURPOSE:     Append action events within a caller transaction or isolated rejection transaction.
 * ----------------------------------------------------------------------------
 * OWNER:       Audit, Reporting & Compliance
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - No token, prompt, rationale, or raw evidence bodies belong in events.
 *   - append requires an existing transaction; rejected uses a new transaction to survive rollback.
 * ============================================================================
 */
package com.fraudengine.audit.api;

public interface AuditApi {
    java.util.List<com.fraudengine.audit.api.dto.AuditEventDto> history(String caseId, long afterSequence, int limit);
    void append(String subject, String type, String caseId, String reference);
    void observe(String subject, String type, String caseId, String reference);
    void rejected(String subject, String type, String caseId, String reference);
}
