package com.fyp.cbc.exception;

import lombok.Getter;

/**
 * Exception thrown when Fineract API calls fail.
 */
@Getter
public class FineractApiException extends RuntimeException {
    
    private final int statusCode;
    private final String errorCode;
    private final String details;
    
    public FineractApiException(String message) {
        super(message);
        this.statusCode = 500;
        this.errorCode = "FINERACT_ERROR";
        this.details = null;
    }
    
    public FineractApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = "FINERACT_ERROR";
        this.details = null;
    }
    
    public FineractApiException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public FineractApiException(String message, int statusCode, String errorCode, String details) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public FineractApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
        this.errorCode = "FINERACT_ERROR";
        this.details = cause.getMessage();
    }
}
