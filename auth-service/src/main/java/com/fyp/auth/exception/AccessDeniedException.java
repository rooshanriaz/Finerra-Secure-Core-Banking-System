package com.fyp.auth.exception;

/**
 * Exception thrown when access is denied (ABAC check fails).
 */
public class AccessDeniedException extends RuntimeException {

    private final String reason;

    public AccessDeniedException(String message) {
        super(message);
        this.reason = message;
    }

    public AccessDeniedException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
