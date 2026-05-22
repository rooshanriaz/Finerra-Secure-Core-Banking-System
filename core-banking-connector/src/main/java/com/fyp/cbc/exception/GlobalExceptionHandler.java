package com.fyp.cbc.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fyp.cbc.dto.response.ApiResponse;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the Core Banking Connector.
 * Provides consistent error responses across all endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle Fineract API exceptions.
     */
    @ExceptionHandler(FineractApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleFineractApiException(FineractApiException ex) {
        log.error("Fineract API error: {} (code: {}, status: {})", 
            ex.getMessage(), ex.getErrorCode(), ex.getStatusCode());
        
        ApiResponse<Void> response = ApiResponse.error(
            ex.getErrorCode(),
            ex.getMessage(),
            ex.getDetails()
        );
        
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }
    
    /**
     * Handle authentication failures.
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailedException(AuthenticationFailedException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.error(
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * Handle resource not found exceptions.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.error(
            "RESOURCE_NOT_FOUND",
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Handle validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
            .success(false)
            .message("Validation failed")
            .data(errors)
            .error(ApiResponse.ErrorDetails.builder()
                .code("VALIDATION_ERROR")
                .message("One or more fields have validation errors")
                .build())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle circuit breaker open state.
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        log.error("Circuit breaker is open: {}", ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.error(
            "SERVICE_UNAVAILABLE",
            "The banking service is temporarily unavailable. Please try again later.",
            "Circuit breaker is open due to multiple failures"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Handle WebClient request errors (connection issues).
     */
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebClientRequestException(WebClientRequestException ex) {
        log.error("Failed to connect to external service: {}", ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.error(
            "CONNECTION_ERROR",
            "Failed to connect to the banking service",
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Handle WebClient response errors.
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebClientResponseException(WebClientResponseException ex) {
        log.error("External service error: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        
        String errorCode = switch (ex.getStatusCode().value()) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 422 -> "UNPROCESSABLE_ENTITY";
            default -> "EXTERNAL_SERVICE_ERROR";
        };
        
        ApiResponse<Void> response = ApiResponse.error(
            errorCode,
            "External service returned an error",
            ex.getResponseBodyAsString()
        );
        
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }
    
    /**
     * Handle Spring Security authentication exceptions.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Spring Security authentication failed: {}", ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.error(
            "AUTH_REQUIRED",
            "Authentication is required to access this resource"
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * Handle access denied exceptions.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.error(
            "ACCESS_DENIED",
            "You do not have permission to access this resource"
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    /**
     * Handle all other unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        
        ApiResponse<Void> response = ApiResponse.error(
            "INTERNAL_ERROR",
            "An unexpected error occurred. Please try again later.",
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
