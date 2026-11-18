/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/CapacityAdvice.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Map capacity rejection to HTTP 429 with a concrete Retry-After header.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - The response contains only a bounded reason and interval, never source or credential data.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.CapacityRejectedException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class CapacityAdvice {
    @ExceptionHandler(CapacityRejectedException.class)
    public ResponseEntity<Map<String, Object>> rejected(CapacityRejectedException failure) {
        return ResponseEntity.status(429).header("Retry-After", Integer.toString(failure.retryAfterSeconds()))
                .body(Map.of("error", "Request capacity exceeded", "retryAfterSeconds", failure.retryAfterSeconds()));
    }
}
