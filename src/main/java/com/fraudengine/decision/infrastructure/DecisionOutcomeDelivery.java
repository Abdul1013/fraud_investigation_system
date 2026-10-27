/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/infrastructure/DecisionOutcomeDelivery.java
 * CONTEXT:     decision
 * LAYER:       infrastructure
 * PURPOSE:     Own decision-outbox polling without leaking repository access across contexts.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-29
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 29
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Redelivery is expected. Workflow signals are idempotent observations, not execution authority.
 * ============================================================================
 */
package com.fraudengine.decision.infrastructure;

import com.fraudengine.decision.api.DecisionOutcomes;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DecisionOutcomeDelivery implements DecisionOutcomes {
    private final JdbcTemplate jdbc;
    private final com.fraudengine.platform.api.OperationalTelemetry telemetry;
    public DecisionOutcomeDelivery(JdbcTemplate jdbc,com.fraudengine.platform.api.OperationalTelemetry telemetry) {this.telemetry=telemetry;this.jdbc=jdbc;}
    @Override public List<Outcome> pending(int limit) {
        telemetry.backlog(com.fraudengine.platform.api.OperationalTelemetry.Queue.DECISION,jdbc.queryForObject("SELECT count(*) FROM decision.outcome_outbox WHERE delivered_at IS NULL",Long.class));
        if(limit<1||limit>100)throw new IllegalArgumentException("Invalid outbox batch");
        return jdbc.query("SELECT o.sequence,o.recommendation_id,o.state,min(p.operation_key) operation_key"
                +" FROM decision.outcome_outbox o LEFT JOIN decision.proposal_operations p ON p.recommendation_id=o.recommendation_id"
                +" WHERE o.delivered_at IS NULL GROUP BY o.sequence,o.recommendation_id,o.state ORDER BY o.sequence LIMIT ?",
                (r,n)->new Outcome(r.getLong(1),r.getObject(2,UUID.class),r.getString(3),r.getString(4)),limit);
    }
    @Override public void delivered(long id) {
        jdbc.update("UPDATE decision.outcome_outbox SET delivered_at=now() WHERE sequence=?",id);
    }
}
