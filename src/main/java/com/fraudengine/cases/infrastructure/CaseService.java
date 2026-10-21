/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/cases/infrastructure/CaseService.java
 * CONTEXT:     cases
 * LAYER:       infrastructure
 * PURPOSE:     Implements typed case reads using authenticated capabilities and persisted grants.
 * ----------------------------------------------------------------------------
 * OWNER:       Case Management
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Every public method resolves CurrentCaller; direct service calls cannot bypass HTTP authorization.
 *   - Repeatable-read transactions keep grant, metadata, and source reads on a consistent snapshot.
 *   - Grant clearance also covers the case summary, not only retrieved evidence.
 * ============================================================================
 */
package com.fraudengine.cases.infrastructure;

import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.cases.api.dto.CaseDto;
import com.fraudengine.cases.api.dto.CaseEvidenceScope;
import com.fraudengine.platform.api.AccessDeniedException;
import com.fraudengine.platform.api.CallerContext;
import com.fraudengine.platform.api.CurrentCaller;
import com.fraudengine.platform.api.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CaseService implements CaseApi {
    private final com.fraudengine.cases.domain.CaseReadPolicy policy = new com.fraudengine.cases.domain.CaseReadPolicy();
    private final CaseRepository repository;
    private final CurrentCaller identity;

    public CaseService(CaseRepository repository, CurrentCaller identity) {
        this.repository = repository;
        this.identity = identity;
    }

    @Override
    public CaseDto getCase(String caseId) {
        return requireGranted(caseId, identity.requireAuthenticated()).details();
    }

    @Override
    public CaseEvidenceScope evidenceScope(String caseId) {
        var caller = identity.requireAuthenticated();
        if (!caller.hasPermission(Permission.READ_EVIDENCE)) {
            throw new AccessDeniedException();
        }
        var granted = requireGranted(caseId, caller);
        return new CaseEvidenceScope(caseId, granted.ceiling(), repository.sourceIds(caseId));
    }

    private CaseRepository.GrantedCase requireGranted(String caseId, CallerContext caller) {
        // Check capability before accessing persistence; assignment comes only from the repository.
        if (!caller.hasPermission(Permission.READ_CASE) || caseId == null || caseId.isBlank()
                || caseId.length() > 100) {
            throw new AccessDeniedException();
        }
        var granted = repository.findGranted(caseId, caller.subject()).orElseThrow(AccessDeniedException::new);
        policy.requireRead(caller, caseId, java.util.Set.of(granted.details().caseId()));
        return granted;
    }
}
