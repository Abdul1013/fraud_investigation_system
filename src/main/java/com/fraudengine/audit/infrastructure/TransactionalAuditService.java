/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/audit/infrastructure/TransactionalAuditService.java
 * CONTEXT:     audit
 * LAYER:       infrastructure
 * PURPOSE:     Owns audit SQL and enforces synchronous transactional append.
 * ----------------------------------------------------------------------------
 * OWNER:       Audit, Reporting & Compliance
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - MANDATORY prevents an action from silently recording audit in a different transaction.
 *   - REQUIRES_NEW records denied commands independently; no foreign key locks link audit to actions.
 *   - Database triggers reject ordinary mutation; a privileged database owner is not tamper-proof.
 * ============================================================================
 */
package com.fraudengine.audit.infrastructure;

import com.fraudengine.audit.api.AuditApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalAuditService implements AuditApi {
    private final JdbcTemplate jdbc;

    private final com.fraudengine.platform.api.CurrentCaller identity;
    private final com.fraudengine.cases.api.CaseApi cases;
    public TransactionalAuditService(JdbcTemplate jdbc, com.fraudengine.platform.api.CurrentCaller identity,
            com.fraudengine.cases.api.CaseApi cases) {
        this.jdbc = jdbc; this.identity = identity; this.cases = cases;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.fraudengine.audit.api.dto.AuditEventDto> history(String caseId, long afterSequence, int limit) {
        var caller = identity.requireAuthenticated();
        if (!caller.hasPermission(com.fraudengine.platform.api.Permission.READ_AUDIT)
                || !"C2".equals(cases.evidenceScope(caseId).classificationCeiling())) {
            throw new com.fraudengine.platform.api.AccessDeniedException();
        }
        if (afterSequence < 0 || limit < 1 || limit > 200) throw new IllegalArgumentException("Invalid audit cursor");
        return jdbc.query("SELECT sequence,occurred_at,subject,event_type,reference_id FROM audit.action_events"
                + " WHERE case_id=? AND sequence>? ORDER BY sequence LIMIT ?", (row, index) ->
                new com.fraudengine.audit.api.dto.AuditEventDto(row.getLong(1), row.getTimestamp(2).toInstant(),
                        row.getString(3), row.getString(4), row.getString(5)), caseId, afterSequence, limit);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String subject, String type, String caseId, String reference) {
        insert(subject, type, caseId, reference);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void observe(String subject, String type, String caseId, String reference) {
        insert(subject, type, caseId, reference);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(String subject, String type, String caseId, String reference) {
        insert(subject, type, caseId, reference);
    }

    private void insert(String subject, String type, String caseId, String reference) {
        jdbc.update("INSERT INTO audit.action_events(subject,event_type,case_id,reference_id) VALUES (?,?,?,?)",
                subject, type, caseId, reference);
    }
}
