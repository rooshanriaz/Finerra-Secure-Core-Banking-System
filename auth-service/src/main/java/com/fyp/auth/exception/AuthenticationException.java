package com.fyp.auth.exception;

/**
 * Exception thrown when authentication fails.
 */
public class AuthenticationException extends RuntimeException {

    private final String errorCode;

    public AuthenticationException(String message) {
        super(message);
        this.errorCode = "AUTH_FAILED";
    }

    public AuthenticationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AUTH_FAILED";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
