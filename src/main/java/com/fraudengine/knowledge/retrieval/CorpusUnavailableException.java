/**
 *  Explicit operational failure for CorpusUnavailable. A missing published corpus or
 * model-specific index is different from a valid query with no evidence; keeping this distinction
 * lets the HTTP API return a useful setup error instead of inventing an answer.
 */
package com.fraudengine.knowledge.retrieval;

public class CorpusUnavailableException extends RuntimeException {
    public CorpusUnavailableException(String message) {
        super(message);
    }
}
