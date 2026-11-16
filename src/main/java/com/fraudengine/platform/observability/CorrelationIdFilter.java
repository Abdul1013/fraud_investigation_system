/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/observability/CorrelationIdFilter.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Return a server-generated request correlation identifier without logging request content.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - A valid bounded trace ID correlates asynchronous work; arbitrary correlation strings are not echoed.
 *   - This filter records no headers, query text, credentials or raw model/evidence bodies.
 * ============================================================================
 */
package com.fraudengine.platform.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private final org.springframework.beans.factory.ObjectProvider<com.fraudengine.platform.api.OperationalTelemetry> telemetry;

    public CorrelationIdFilter(
            org.springframework.beans.factory.ObjectProvider<com.fraudengine.platform.api.OperationalTelemetry> telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var observer = telemetry.getIfAvailable();
        String correlation = observer == null ? null : observer.traceId();
        if (correlation == null)
            correlation = UUID.randomUUID().toString();
        response.setHeader("X-Correlation-Id", correlation);
        org.slf4j.MDC.put("correlationId", correlation);
        try {
            chain.doFilter(request, response);
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }
    }
}
