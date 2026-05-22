package com.fyp.cbc.exception;

import lombok.Getter;

/**
 * Exception thrown when a requested resource is not found.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {
    
    private final String resourceType;
    private final String resourceId;
    
    public ResourceNotFoundException(String resourceType, Long resourceId) {
        super(String.format("%s with ID %d not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = String.valueOf(resourceId);
    }
    
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("%s with ID %s not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = "Resource";
        this.resourceId = "unknown";
    }
}
