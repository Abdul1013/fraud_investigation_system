/**
 * Distinguish unavailable or malformed provider output from a valid unknown answer. Only
 * a short operational message reaches clients; raw prompts and confidential source text are not
 * logged.
 */
package com.fraudengine.platform.gateway;

public class ModelUnavailableException extends RuntimeException {
    public ModelUnavailableException(String message) {
        super(message);
    }

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
