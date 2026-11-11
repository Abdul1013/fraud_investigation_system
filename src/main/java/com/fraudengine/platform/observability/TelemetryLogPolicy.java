/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/observability/TelemetryLogPolicy.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Prevent framework body/debug logging from disclosing prompts, evidence or credentials.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-32
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Apply before readiness; known body/validation resolvers are disabled because their messages can contain request values.
 *   - Operational diagnosis uses bounded outcomes, trace IDs and authorized audit data rather than raw request bodies.
 * ============================================================================
 */
package com.fraudengine.platform.observability;
import org.springframework.boot.*;
import org.springframework.boot.logging.*;
import org.springframework.stereotype.Component;
@Component
public class TelemetryLogPolicy implements ApplicationRunner {
    @Override public void run(ApplicationArguments arguments) {
        var logging=LoggingSystem.get(getClass().getClassLoader());
        for(var configuration:logging.getLoggerConfigurations()) {
            var level=configuration.getConfiguredLevel();
            if(level==LogLevel.DEBUG||level==LogLevel.TRACE)logging.setLogLevel(configuration.getName(),LogLevel.INFO);
        }
        for(String name:java.util.List.of(
            "org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor",
            "org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver",
            "org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver"))logging.setLogLevel(name,LogLevel.OFF);
    }
}
