/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/cases/domain/CaseReadPolicyTest.java
 * CONTEXT:     cases
 * LAYER:       test
 * PURPOSE:     Checks that case assignments and capabilities cannot substitute for each other.
 * ----------------------------------------------------------------------------
 * OWNER:       Case Management
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Pure policy tests simulate trusted server inputs without claiming HTTP authentication is wired.
 *   - The adversarial cases cover unrelated-case reads, absent identity, and missing capabilities.
 * ============================================================================
 */
package com.fraudengine.cases.domain;

import com.fraudengine.platform.api.AccessDeniedException;
import com.fraudengine.platform.api.CallerContext;
import com.fraudengine.platform.api.Permission;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseReadPolicyTest {
    private final CaseReadPolicy policy = new CaseReadPolicy();
    private final CallerContext reader = new CallerContext("investigator-1", Set.of(Permission.READ_CASE));

    @Test
    void permitsAssignedCaseWithReadCapability() {
        assertThatCode(() -> policy.requireRead(reader, "case-1", Set.of("case-1")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnrelatedCaseEvenWithApprovalCapability() {
        var analyst = new CallerContext("analyst-1", Set.of(Permission.READ_CASE, Permission.APPROVE_ACTION));
        assertDenied(analyst, "case-2", Set.of("case-1"));
    }

    @Test
    void rejectsAssignedCaseWithoutReadCapability() {
        assertDenied(new CallerContext("actor-1", Set.of(Permission.PROPOSE_ACTION)), "case-1", Set.of("case-1"));
    }

    @Test
    void failsClosedForMissingIdentityOrAssignmentsOrCase() {
        assertDenied(null, "case-1", Set.of("case-1"));
        assertDenied(reader, "case-1", null);
        assertDenied(reader, null, Set.of("case-1"));
        assertDenied(reader, " ", Set.of(" "));
    }

    @Test
    void callerPermissionsCannotBeMutatedAfterConstruction() {
        var permissions = new HashSet<>(Set.of(Permission.READ_CASE));
        var caller = new CallerContext("actor-1", permissions);
        permissions.add(Permission.APPROVE_ACTION);
        assertThat(caller.hasPermission(Permission.APPROVE_ACTION)).isFalse();
        assertThatThrownBy(() -> caller.permissions().add(Permission.APPROVE_ACTION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertDenied(CallerContext caller, String caseId, Set<String> assignments) {
        assertThatThrownBy(() -> policy.requireRead(caller, caseId, assignments))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Access denied");
    }
}
