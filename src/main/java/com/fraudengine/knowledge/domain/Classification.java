/**
 * FILE:        src/main/java/com/fraudengine/knowledge/domain/Classification.java
 * CONTEXT:     knowledge
 * LAYER:       domain
 * PURPOSE:     Declares the Classification scaffold type for the Knowledge & Evidence context.
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-2
 * RELATED:     ADR-005
 * NOTES:
 *   - Preserve context boundaries and keep business behavior out of the scaffold.
 */
package com.fraudengine.knowledge.domain;

public enum Classification {
    C0_PUBLIC("C0"),
    C1_INTERNAL("C1"),
    C2_CONFIDENTIAL("C2"),
    C3_RESTRICTED("C3");

    private final String wire;
    Classification(String wire) { this.wire = wire; }
    public String wire() { return wire; }
    public static Classification fromWire(String w) {
        for (Classification c : values()) if (c.wire.equals(w)) return c;
        throw new IllegalArgumentException("Unknown classification: " + w);
    }
}