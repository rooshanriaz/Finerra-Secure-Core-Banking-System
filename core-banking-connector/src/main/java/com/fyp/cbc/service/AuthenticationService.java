package com.fyp.cbc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fyp.cbc.client.AuthenticationClient;
import com.fyp.cbc.dto.request.AuthRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.AuthResponse;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for authentication operations.
 * Wraps the AuthenticationClient with additional business logic.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    
    private final AuthenticationClient authenticationClient;
    
    /**
     * Authenticate a user and return wrapped response.
     * 
     * @param username User's username
     * @param password User's password
     * @return API response containing authentication result
     */
    public ApiResponse<AuthResponse> authenticate(String username, String password) {
        log.info("Authenticating user: {}", username);
        
        AuthRequest request = AuthRequest.builder()
            .username(username)
            .password(password)
            .build();
        
        AuthResponse authResponse = authenticationClient.authenticate(request);
        
        if (authResponse != null && authResponse.isAuthenticated()) {
            log.info("User {} authenticated successfully", username);
            return ApiResponse.success("Authentication successful", authResponse);
        }
        
        log.warn("Authentication failed for user: {}", username);
        return ApiResponse.error("AUTH_FAILED", "Authentication failed");
    }
    
    /**
     * Authenticate with request object.
     * 
     * @param request Authentication request
     * @return API response containing authentication result
     */
    public ApiResponse<AuthResponse> authenticate(AuthRequest request) {
        return authenticate(request.getUsername(), request.getPassword());
    }
    
    /**
     * Authenticate and get user roles and permissions.
     * 
     * @param username User's username
     * @param password User's password
     * @return API response with roles and permissions
     */
    public ApiResponse<AuthResponse> authenticateWithRoles(String username, String password) {
        log.info("Self-authenticating user: {}", username);
        
        AuthRequest request = AuthRequest.builder()
            .username(username)
            .password(password)
            .build();
        
        AuthResponse authResponse = authenticationClient.authenticateSelf(request);
        
        if (authResponse != null && authResponse.isAuthenticated()) {
            log.info("User {} authenticated with {} roles", 
                username, authResponse.getRoles() != null ? authResponse.getRoles().size() : 0);
            return ApiResponse.success("Authentication successful", authResponse);
        }
        
        return ApiResponse.error("AUTH_FAILED", "Authentication failed");
    }
    
    /**
     * Validate if user has specific permission.
     * 
     * @param authResponse Authentication response to check
     * @param permission Permission code to validate
     * @return true if user has permission
     */
    public boolean hasPermission(AuthResponse authResponse, String permission) {
        if (authResponse == null || authResponse.getPermissions() == null) {
            return false;
        }
        return authResponse.getPermissions().contains(permission);
    }
    
    /**
     * Validate if user has specific role.
     * 
     * @param authResponse Authentication response to check
     * @param roleName Role name to validate
     * @return true if user has role
     */
    public boolean hasRole(AuthResponse authResponse, String roleName) {
        if (authResponse == null || authResponse.getRoles() == null) {
            return false;
        }
        return authResponse.getRoles().stream()
            .anyMatch(role -> role.getName().equalsIgnoreCase(roleName));
    }
}
