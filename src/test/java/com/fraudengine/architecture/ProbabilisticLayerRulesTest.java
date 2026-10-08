/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/architecture/ProbabilisticLayerRulesTest.java
 * CONTEXT:     architecture
 * LAYER:       test
 * PURPOSE:     Prevents provider adapter dependencies from leaking outside the gateway.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-26
 * RELATED:     ADR-003
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - TODO(week-26): add checks for probabilistic-layer write capabilities.
 *   - Provider adapters remain empty placeholders in this scaffold.
 * ============================================================================
 */
package com.fraudengine.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class ProbabilisticLayerRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.fraudengine");

    @Test
    void providerSdkImportsStayBehindGatewayBoundary() {
        ArchRuleDefinition.noClasses()
            .that().resideOutsideOfPackages("com.fraudengine.platform.gateway..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fraudengine.platform.gateway.provider..")
            .because("provider SDKs must not leak outside the gateway")
            .check(CLASSES);
    }
}