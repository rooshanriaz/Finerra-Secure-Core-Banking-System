package com.fyp.cbc.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private boolean success;
    private String message;
    private T data;
    private ErrorDetails error;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetails {
        private String code;
        private String message;
        private String details;
    }
    
    /**
     * Create a success response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .message("Operation completed successfully")
            .data(data)
            .build();
    }
    
    /**
     * Create a success response with custom message.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }
    
    /**
     * Create an error response.
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .message("Operation failed")
            .error(ErrorDetails.builder()
                .code(code)
                .message(message)
                .build())
            .build();
    }
    
    /**
     * Create an error response with details.
     */
    public static <T> ApiResponse<T> error(String code, String message, String details) {
        return ApiResponse.<T>builder()
            .success(false)
            .message("Operation failed")
            .error(ErrorDetails.builder()
                .code(code)
                .message(message)
                .details(details)
                .build())
            .build();
    }
}
