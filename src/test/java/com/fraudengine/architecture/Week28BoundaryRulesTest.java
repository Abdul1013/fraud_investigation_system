/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/architecture/Week28BoundaryRulesTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Prevent the new tool layer from importing protected context internals or calling execution.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Imports production classes only; test fixtures may reach adapters for fault injection.
 *   - This targeted rule strengthens new paths without pretending legacy scaffold boundaries are complete.
 * ============================================================================
 */
package com.fraudengine.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.fraudengine.decision.api.DecisionApi;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Week28BoundaryRulesTest {
    private final com.tngtech.archunit.core.domain.JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.fraudengine");

    @Test
    void orchestrationUsesOnlyOtherContextsPublicApis() {
        ArchRuleDefinition.noClasses().that().resideInAPackage("com.fraudengine.orchestration..")
                .should().dependOnClassesThat().resideInAnyPackage("com.fraudengine.cases.domain..",
                        "com.fraudengine.cases.infrastructure..", "com.fraudengine.knowledge.retrieval..",
                        "com.fraudengine.knowledge.infrastructure..", "com.fraudengine.decision.domain..",
                        "com.fraudengine.decision.infrastructure..", "com.fraudengine.audit.infrastructure..")
                .check(classes);
    }

    @Test
    void probabilisticOrchestrationCannotCallApprovalOrExecution() {
        ArchRuleDefinition.noClasses().that().resideInAPackage("com.fraudengine.orchestration..")
                .should().callMethod(DecisionApi.class, "execute", UUID.class, String.class, String.class)
                .orShould().callMethod(DecisionApi.class, "approve", UUID.class).check(classes);
    }
}
