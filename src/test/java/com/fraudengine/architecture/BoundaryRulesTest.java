/**
 * WEEK 27 — Architecture checks import production classes only. Tests deliberately reach adapters
 * to verify failures; treating those test dependencies as application wiring would create false
 * violations. Existing structural rules remain, with runtime safety verified separately by
 * integration and grounding tests. This separation prevents tests from weakening the design.
 */
/**
 * ============================================================================ FILE:
 * src/test/java/com/fraudengine/architecture/BoundaryRulesTest.java CONTEXT: architecture LAYER:
 * test PURPOSE: Enforces cross-context access through public API packages.
 * ---------------------------------------------------------------------------- OWNER: Platform &
 * Operations SINCE: week-26 RELATED: ADR-001
 * ---------------------------------------------------------------------------- NOTES: -
 * TODO(week-26): tighten the rule to validate target context API packages. - Package boundaries are
 * checked against compiled production classes.
 * ============================================================================
 */
package com.fraudengine.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import org.junit.jupiter.api.Test;

class BoundaryRulesTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(
                            com.tngtech.archunit.core.importer.ImportOption.Predefined
                                    .DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.fraudengine");

    @Test
    void noCrossContextImportWithoutApiLayer() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAnyPackage("com.fraudengine..")
                .and()
                .resideOutsideOfPackage("..api..")
                .and()
                .resideOutsideOfPackage("com.fraudengine.ingestion..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.fraudengine.ingestion..")
                .because("bounded contexts must not directly import each other")
                .check(CLASSES);
    }
}
