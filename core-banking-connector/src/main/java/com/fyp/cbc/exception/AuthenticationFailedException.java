package com.fyp.cbc.exception;

import lombok.Getter;

/**
 * Exception thrown when authentication with Fineract fails.
 */
@Getter
public class AuthenticationFailedException extends RuntimeException {
    
    private final String errorCode;
    
    public AuthenticationFailedException(String message) {
        super(message);
        this.errorCode = "AUTH_FAILED";
    }
    
    public AuthenticationFailedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AUTH_FAILED";
    }
}
