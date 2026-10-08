/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/architecture/BoundaryRulesTest.java
 * CONTEXT:     architecture
 * LAYER:       test
 * PURPOSE:     Enforces cross-context access through public API packages.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-26
 * RELATED:     ADR-001
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - TODO(week-26): tighten the rule to validate target context API packages.
 *   - Package boundaries are checked against compiled production classes.
 * ============================================================================
 */
package com.fraudengine.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class BoundaryRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.fraudengine");

    @Test
    void noCrossContextImportWithoutApiLayer() {
        ArchRuleDefinition.noClasses()
            .that().resideInAnyPackage("com.fraudengine..")
            .and().resideOutsideOfPackage("..api..")
            .and().resideOutsideOfPackage("com.fraudengine.ingestion..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fraudengine.ingestion..")
            .because("bounded contexts must not directly import each other")
            .check(CLASSES);
    }
}
