/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/AccessDeniedAdvice.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Maps domain-neutral access denials to a consistent HTTP response.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Case services use the public exception without importing Spring Security transport types.
 *   - No subject, grant, or case existence is disclosed in the error message.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.AccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccessDeniedAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> denied() {
        return Map.of("error", "Access denied");
    }
}
