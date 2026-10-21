/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/InvestigationRepository.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Owns persisted launch idempotency and compare-and-set investigation transitions.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Each transition increments a version and appends its event in the same transaction.
 *   - Repeated launches never enqueue the model again; mismatched payloads conflict.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure;

import com.fraudengine.orchestration.api.OrchestrationApi;
import com.fraudengine.platform.api.AccessDeniedException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InvestigationRepository {
    private final JdbcTemplate jdbc;
    private final com.fraudengine.audit.api.AuditApi audit;
    private final com.fraudengine.platform.api.CurrentCaller identity;
    public InvestigationRepository(JdbcTemplate jdbc, com.fraudengine.audit.api.AuditApi audit,
            com.fraudengine.platform.api.CurrentCaller identity) {
        this.jdbc = jdbc; this.audit = audit; this.identity = identity;
    }

    @Transactional
    public boolean insert(UUID id, String caseId, String subject, String key, String goal) {
        boolean created = jdbc.update("INSERT INTO orchestration.investigations(id,case_id,subject,request_key,goal,state)"
                + " VALUES (?,?,?,?,?,'NEW') ON CONFLICT DO NOTHING", id, caseId, subject, key, goal) == 1;
        if (created) audit.append(subject, "INVESTIGATION_NEW", caseId, id.toString());
        return created;
    }

    public Stored byKey(String subject, String key) {
        return jdbc.query("SELECT * FROM orchestration.investigations WHERE subject=? AND request_key=?",
                (rs, n) -> stored(rs), subject, key).stream().findFirst().orElseThrow();
    }

    public Stored get(UUID id) {
        return jdbc.query("SELECT * FROM orchestration.investigations WHERE id=?", (rs, n) -> stored(rs), id)
                .stream().findFirst().orElseThrow(AccessDeniedException::new);
    }

    @Transactional
    public boolean transition(UUID id, String expected, String next, UUID recommendation, String detail) {
        var versions = jdbc.query("UPDATE orchestration.investigations SET state=?,version=version+1,"
                + " recommendation_id=? WHERE id=? AND state=? RETURNING version", (rs, n) -> rs.getInt(1),
                next, recommendation, id, expected);
        if (versions.isEmpty()) return false;
        jdbc.update("INSERT INTO orchestration.investigation_events(run_id,sequence,event_type,detail) VALUES (?,?,?,?)",
                id, versions.getFirst(), next, detail);
        audit.append(identity.requireAuthenticated().subject(), "INVESTIGATION_" + next, get(id).run().caseId(), id.toString());
        return true;
    }

    @Transactional
    public void toolEvent(UUID id, String detail) {
        var versions = jdbc.query("UPDATE orchestration.investigations SET version=version+1"
                + " WHERE id=? AND state='INVESTIGATING' RETURNING version", (rs, n) -> rs.getInt(1), id);
        if (versions.isEmpty()) throw new IllegalStateException("Investigation is not active");
        jdbc.update("INSERT INTO orchestration.investigation_events(run_id,sequence,event_type,detail) VALUES (?,?,'TOOL_RESULT',?)",
                id, versions.getFirst(), detail);
        audit.append(identity.requireAuthenticated().subject(), "INVESTIGATION_TOOL_RESULT", get(id).run().caseId(), id.toString());
    }

    public List<OrchestrationApi.Event> events(UUID id) {
        return jdbc.query("SELECT sequence,event_type,detail FROM orchestration.investigation_events"
                + " WHERE run_id=? ORDER BY sequence", (rs, n) -> new OrchestrationApi.Event(rs.getInt(1), rs.getString(2), rs.getString(3)), id);
    }

    private Stored stored(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Stored(new OrchestrationApi.Run(rs.getObject("id", UUID.class), rs.getString("case_id"),
                rs.getString("state"), rs.getInt("version"), rs.getObject("recommendation_id", UUID.class)), rs.getString("goal"));
    }
    public record Stored(OrchestrationApi.Run run, String goal) {}
}
