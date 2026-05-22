package com.fyp.cbc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fyp.cbc.dto.request.AuthRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.AuthResponse;
import com.fyp.cbc.service.AuthenticationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for authentication operations.
 * Provides endpoints for user authentication against Fineract.
 */
@RestController
@RequestMapping("/v1/authentication")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication APIs")
public class AuthenticationController {
    
    private final AuthenticationService authenticationService;
    
    /**
     * Authenticate user with username and password.
     * POST /v1/authentication
     */
    @PostMapping
    @Operation(summary = "Authenticate user", 
               description = "Authenticate user credentials and return authentication token")
    public ResponseEntity<ApiResponse<AuthResponse>> authenticate(
            @Valid @RequestBody AuthRequest request) {
        ApiResponse<AuthResponse> response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Authenticate and retrieve user roles and permissions.
     * POST /v1/authentication/self
     */
    @PostMapping("/self")
    @Operation(summary = "Authenticate with roles", 
               description = "Authenticate and retrieve user roles and permissions")
    public ResponseEntity<ApiResponse<AuthResponse>> authenticateWithRoles(
            @Valid @RequestBody AuthRequest request) {
        ApiResponse<AuthResponse> response = authenticationService.authenticateWithRoles(
            request.getUsername(), request.getPassword());
        return ResponseEntity.ok(response);
    }
}
