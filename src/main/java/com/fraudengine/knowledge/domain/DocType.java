// package com.fraudengine.knowledge.domain;

// public final class DocType {
//     public static final String POLICY = "policy";
//     public static final String PRIOR_CASE = "prior_case";
//     public static final String KYC = "kyc";
//     public static final String REGULATORY = "regulatory";
//    private DocType() {
//         // Private constructor to prevent instantiation
//     }
// }

package com.fraudengine.knowledge.domain;

/**
 * The kinds of documents that can exist in the knowledge base.
 *
 * Owned by the Knowledge & Evidence bounded context (ADR-001).
 * Each type carries its own ingestion defaults so that classification
 * and embeddability are structural, not procedural (ADR-005).
 */
public enum DocType {

    POLICY(
        "C0",
        true,
        ChunkStrategy.SEMANTIC_SECTIONS
    ),
    REGULATORY(
        "C0",
        true,
        ChunkStrategy.NUMBERED_SECTIONS
    ),
    PRIOR_CASE(
        "C2",
        true,
        ChunkStrategy.STRUCTURED_FIELDS
    ),
    KYC(
        "C3",
        false,   // never embedded
        ChunkStrategy.NONE
    );

    private final String defaultClassification;
    private final boolean embeddable;
    private final ChunkStrategy chunkStrategy;

    DocType(String defaultClassification, boolean embeddable, ChunkStrategy chunkStrategy) {
        this.defaultClassification = defaultClassification;
        this.embeddable = embeddable;
        this.chunkStrategy = chunkStrategy;
    }

    public String defaultClassification() { return defaultClassification; }
    public boolean isEmbeddable() { return embeddable; }
    public ChunkStrategy chunkStrategy() { return chunkStrategy; }

    /** Wire format used in metadata and the database. */
    public String wireName() { return name().toLowerCase(); }

    public static DocType fromWire(String wire) {
        for (DocType t : values()) {
            if (t.wireName().equals(wire)) return t;
        }
        throw new IllegalArgumentException("Unknown doc_type: " + wire);
    }

    public enum ChunkStrategy {
        SEMANTIC_SECTIONS,
        NUMBERED_SECTIONS,
        STRUCTURED_FIELDS,
        NONE
    }
}