/**
 *  Explicit operational failure for IndexUnavailable. A missing published corpus or
 * model-specific index is different from a valid query with no evidence; keeping this distinction
 * lets the HTTP API return a useful setup error instead of inventing an answer.
 */
package com.fraudengine.knowledge.retrieval;

public class IndexUnavailableException extends RuntimeException {
    public IndexUnavailableException(String message) {
        super(message);
    }
}
