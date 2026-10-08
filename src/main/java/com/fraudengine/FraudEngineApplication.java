/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/FraudEngineApplication.java
 * CONTEXT:     FraudEngineApplication.java
 * LAYER:       config
 * PURPOSE:     Declares the FraudEngineApplication scaffold type for the Platform & Operations context.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-2
 * RELATED:     ADR-001, ADR-003
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Preserve context boundaries and keep business behavior out of the scaffold.
 * ============================================================================
 */
package com.fraudengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FraudEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudEngineApplication.class, args);
    }
}
