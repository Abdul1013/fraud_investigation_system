/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/api/ReadAdmission.java
 * CONTEXT:     platform
 * LAYER:       api
 * PURPOSE:     Bound complete read work across HTTP, worker and local tool transports.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Nested calls share one admission scope; implementations must release it in finally blocks.
 * ============================================================================
 */
package com.fraudengine.platform.api;

import java.util.function.Supplier;

public interface ReadAdmission {
    <T> T call(Supplier<T> read);

    void requireLaunchBudget();
}
