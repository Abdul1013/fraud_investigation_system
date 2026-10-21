/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/cases/infrastructure/CaseRepository.java
 * CONTEXT:     cases
 * LAYER:       infrastructure
 * PURPOSE:     Loads case details only through a subject-specific grant join.
 * ----------------------------------------------------------------------------
 * OWNER:       Case Management
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Parameterized SQL binds the authenticated subject and requested case together.
 *   - Unknown and unassigned cases both return empty; no existence lookup precedes authorization.
 *   - This repository owns only case-management SQL; knowledge access stays behind public APIs.
 * ============================================================================
 */
package com.fraudengine.cases.infrastructure;

import com.fraudengine.cases.api.dto.CaseDto;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRepository {
    private final JdbcTemplate jdbc;

    public CaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<GrantedCase> findGranted(String caseId, String subject) {
        return jdbc.query("""
                SELECT c.case_id, c.status, c.summary, c.classification, g.classification_ceiling
                FROM case_management.case_records c
                JOIN case_management.case_grants g ON g.case_id=c.case_id
                WHERE c.case_id=? AND g.subject=? AND c.classification <= g.classification_ceiling
                """, (row, index) -> new GrantedCase(new CaseDto(row.getString("case_id"),
                        CaseDto.Status.valueOf(row.getString("status")), row.getString("summary")),
                        row.getString("classification_ceiling")), caseId, subject).stream().findFirst();
    }

    public Set<String> sourceIds(String caseId) {
        return Set.copyOf(jdbc.queryForList(
                "SELECT source_id FROM case_management.case_sources WHERE case_id=?", String.class, caseId));
    }

    public record GrantedCase(CaseDto details, String ceiling) {}
}
