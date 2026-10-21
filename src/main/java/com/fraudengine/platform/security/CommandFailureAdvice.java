/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/CommandFailureAdvice.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Return bounded command errors without leaking internal failure messages.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Expected state/idempotency conflicts are 409; malformed direct command arguments are 400.
 *   - Unexpected provider and persistence details are never returned from these handlers.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = {"com.fraudengine.decision.infrastructure", "com.fraudengine.orchestration.infrastructure"})
public class CommandFailureAdvice {
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict() { return Map.of("error", "Command conflicts with current state or configuration"); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid() { return Map.of("error", "Invalid command arguments"); }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> constraintConflict() { return Map.of("error", "Command conflicts with stored state"); }
}
