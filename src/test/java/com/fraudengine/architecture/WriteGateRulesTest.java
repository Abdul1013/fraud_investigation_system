/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/architecture/WriteGateRulesTest.java
 * CONTEXT:     architecture
 * LAYER:       test
 * PURPOSE:     Restricts write-gate ownership to the decision context.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-26
 * RELATED:     ADR-002
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - TODO(week-26): extend this rule to all write-capable ports.
 *   - Write-gate behavior remains unimplemented.
 * ============================================================================
 */
package com.fraudengine.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class WriteGateRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.fraudengine");

    @Test
    void onlyDecisionContextMayUseWriteGate() {
        ArchRuleDefinition.noClasses()
            .that().resideOutsideOfPackages("com.fraudengine.decision..")
            .should().dependOnClassesThat().haveSimpleName("WriteGate")
            .because("only the decision context can enforce write authority")
            .check(CLASSES);
    }
}
