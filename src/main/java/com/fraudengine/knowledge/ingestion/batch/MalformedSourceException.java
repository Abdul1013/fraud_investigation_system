package com.fraudengine.knowledge.ingestion.batch;

/**
 * Carries source identity through a skipped read without exposing document
 * text.
 */
public class MalformedSourceException extends IllegalArgumentException {
    private final String sourceId;

    public MalformedSourceException(String sourceId, IllegalArgumentException cause) {
        super(cause.getMessage(), cause);
        this.sourceId = sourceId;
    }

    public String sourceId() {
        return sourceId;
    }
}
