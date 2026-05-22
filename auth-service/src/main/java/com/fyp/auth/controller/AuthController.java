package com.fyp.auth.controller;

import com.fyp.auth.dto.request.LoginRequest;
import com.fyp.auth.dto.response.ApiResponse;
import com.fyp.auth.dto.response.AuthResponse;
import com.fyp.auth.service.AuthenticationService;
import com.fyp.auth.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller.
 * Handles login, logout, token refresh, and validation.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Login endpoint.
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        log.info("Login request for user: {} from IP: {}", request.getUsername(), clientIp);
        
        AuthResponse response = authenticationService.login(request, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint.
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        String token = extractToken(authHeader);
        
        authenticationService.logout(token, clientIp);
        
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    /**
     * Refresh token endpoint.
     * POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestHeader("X-Refresh-Token") String refreshToken,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        
        AuthResponse response = authenticationService.refreshToken(refreshToken, clientIp);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate token endpoint.
     * POST /api/v1/auth/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        
        String token = extractToken(authHeader);
        boolean valid = authenticationService.validateToken(token);
        
        return ResponseEntity.ok(ApiResponse.success("Token validation result", valid));
    }

    /**
     * Get current user info (from token).
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<String>> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        // This would be implemented with proper token parsing
        // For now, return a placeholder
        return ResponseEntity.ok(ApiResponse.success("Current user endpoint - implement with JWT filter"));
    }

    /**
     * Extract token from Authorization header.
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    /**
     * Get client IP address.
     */
    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }
}
