package com.fyp.auth.service;

import com.fyp.auth.dto.request.LoginRequest;
import com.fyp.auth.dto.response.AuthResponse;
import com.fyp.auth.entity.AuditLog;
import com.fyp.auth.entity.Permission;
import com.fyp.auth.entity.Role;
import com.fyp.auth.entity.RevokedToken;
import com.fyp.auth.entity.User;
import com.fyp.auth.exception.AccessDeniedException;
import com.fyp.auth.exception.AuthenticationException;
import com.fyp.auth.repository.UserRepository;
import com.fyp.auth.service.DidVerificationService.DidVerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for authentication operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RevocationService revocationService;
    private final LightAbacService lightAbacService;
    private final AuditService auditService;
    private final TotpService totpService;
    private final DidVerificationService didVerificationService;

    /**
     * Authenticate user and issue tokens.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp, String userAgent) {
        log.info("Login attempt for user: {} from IP: {}", request.getUsername(), clientIp);

        // Check for brute force (too many failures from this IP)
        long recentFailures = auditService.countRecentLoginFailuresFromIp(clientIp, 15);
        if (recentFailures >= 10) {
            log.warn("Too many login failures from IP: {}", clientIp);
            auditService.logSuspiciousActivity(null, request.getUsername(), clientIp,
                    "Brute force attack detected", "IP blocked due to " + recentFailures + " failed attempts");
            throw new AuthenticationException("Too many failed attempts. Please try again later.", "RATE_LIMITED");
        }

        // Find user
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null) {
            auditService.logLoginFailure(request.getUsername(), clientIp, userAgent, "User not found");
            throw new AuthenticationException("Invalid username or password", "INVALID_CREDENTIALS");
        }

        // Check if account is locked
        if (!user.isAccountNonLocked()) {
            if (user.getLockedAt() != null) {
                LocalDateTime unlockTime = user.getLockedAt().plusMinutes(LOCK_DURATION_MINUTES);
                if (LocalDateTime.now().isAfter(unlockTime)) {
                    // Auto-unlock after duration
                    userRepository.unlockAccount(user.getId());
                    log.info("Account auto-unlocked for user: {}", user.getUsername());
                } else {
                    auditService.logLoginFailure(request.getUsername(), clientIp, userAgent, "Account locked");
                    throw new AuthenticationException("Account is locked. Please try again later.", "ACCOUNT_LOCKED");
                }
            }
        }

        // Check if account is enabled
        if (!user.isEnabled()) {
            auditService.logLoginFailure(request.getUsername(), clientIp, userAgent, "Account disabled");
            throw new AuthenticationException("Account is disabled", "ACCOUNT_DISABLED");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            userRepository.incrementFailedAttempts(user.getId());
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                userRepository.lockAccount(user.getId(), LocalDateTime.now());
                log.warn("Account locked due to failed attempts: {}", user.getUsername());
                auditService.log(AuditLog.AuditAction.USER_LOCKED, user.getId(), user.getUsername(),
                        "USER", user.getId(),
                        "Account locked automatically after " + failedAttempts + " failed login attempts",
                        clientIp, userAgent, null, true, null,
                        "{\"source\":\"failed_login_threshold\"}");
                auditService.logLoginFailure(request.getUsername(), clientIp, userAgent,
                        "Account locked after " + failedAttempts + " failed attempts");
            } else {
                auditService.logLoginFailure(request.getUsername(), clientIp, userAgent, "Invalid password");
            }
            
            throw new AuthenticationException("Invalid username or password", "INVALID_CREDENTIALS");
        }

        // ABAC check (IP whitelist + business hours)
        if (!lightAbacService.isAccessAllowed(user.getId(), user.getUsername(), clientIp)) {
            throw new AccessDeniedException("Access denied based on security policy");
        }

        // If MFA is enabled, issue a short-lived MFA token instead of full tokens
        if (user.isMfaEnabled() && user.getMfaSecret() != null) {
            log.info("MFA required for user: {}", user.getUsername());
            String mfaToken = tokenService.generateMfaToken(user);

            auditService.logLoginSuccess(user.getId(), user.getUsername(), clientIp, userAgent);

            return AuthResponse.builder()
                    .success(true)
                    .message("MFA verification required")
                    .mfaRequired(true)
                    .mfaToken(mfaToken)
                    .build();
        }

        // No MFA — complete login with optional DID verification
        return completeLogin(user, clientIp, userAgent);
    }

    /**
     * Verify MFA TOTP code and complete login.
     */
    @Transactional
    public AuthResponse verifyMfaAndLogin(String mfaToken, String totpCode, String clientIp, String userAgent) {
        if (!tokenService.validateMfaToken(mfaToken)) {
            throw new AuthenticationException("Invalid or expired MFA token", "INVALID_MFA_TOKEN");
        }

        String username = tokenService.extractUsername(mfaToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("User not found", "USER_NOT_FOUND"));

        if (!user.isMfaEnabled() || user.getMfaSecret() == null) {
            throw new AuthenticationException("MFA is not enabled for this account", "MFA_NOT_ENABLED");
        }

        if (!totpService.verifyCode(user.getMfaSecret(), totpCode)) {
            log.warn("Invalid TOTP code for user: {}", username);
            auditService.logSuspiciousActivity(user.getId(), username, clientIp,
                    "Invalid MFA code", "Failed TOTP verification");
            throw new AuthenticationException("Invalid verification code", "INVALID_MFA_CODE");
        }

        user.setMfaVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("MFA verified for user: {}", username);
        return completeLogin(user, clientIp, userAgent);
    }

    /**
     * Complete login after all authentication factors are satisfied.
     * Performs DID verification and issues full JWT tokens.
     */
    private AuthResponse completeLogin(User user, String clientIp, String userAgent) {
        boolean firstLogin = user.getLastLoginAt() == null;

        // DID verification (non-blocking — login succeeds even if DID check fails)
        boolean didVerified = false;
        if (user.getDidIdentifier() != null && !user.getDidIdentifier().isBlank()) {
            DidVerificationResult didResult = didVerificationService.verifyDid(user.getDidIdentifier());
            didVerified = didResult.verified();
            log.info("DID verification for {}: verified={}, message={}", 
                    user.getUsername(), didResult.verified(), didResult.message());
        }

        // Generate full tokens
        String accessToken = tokenService.generateAccessToken(user, didVerified);
        String refreshToken = tokenService.generateRefreshToken(user);
        String idToken = tokenService.generateIdToken(user, "finnera-web");

        // Update last login
        userRepository.updateLastLogin(user.getId(), LocalDateTime.now(), clientIp);

        // Audit success
        auditService.logLoginSuccess(user.getId(), user.getUsername(), clientIp, userAgent);

        // Build response
        Set<String> roles = user.getRoles().stream()
                .filter(Role::isEnabled)
                .map(Role::getName)
                .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
                .filter(Role::isEnabled)
                .flatMap(role -> role.getPermissions().stream())
                .filter(Permission::isEnabled)
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .permissions(permissions)
                .build();

        return AuthResponse.builder()
                .success(true)
                .message("Login successful")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .idToken(idToken)
                .tokenType("Bearer")
                .expiresIn(tokenService.getAccessTokenExpiration() / 1000)
                .user(userInfo)
                .mfaRequired(false)
                .didVerified(didVerified)
                .firstLogin(firstLogin)
                .mfaSetupRequired(!user.isMfaEnabled())
                .build();
    }

    /**
     * Logout user (revoke token).
     */
    @Transactional
    public void logout(String token, String clientIp) {
        try {
            String jti = tokenService.extractJti(token);
            Long userId = tokenService.extractUserId(token);
            String username = tokenService.extractUsername(token);
            Date expiration = tokenService.extractExpiration(token);

            revocationService.revokeToken(jti, userId, username, expiration,
                    RevokedToken.RevocationReason.LOGOUT, username, clientIp, "User initiated logout");

            log.info("User {} logged out successfully", username);
        } catch (Exception e) {
            log.warn("Error during logout: {}", e.getMessage());
            // Don't fail logout even if token parsing fails
        }
    }

    /**
     * Refresh access token using refresh token.
     * Implements refresh token rotation: issues both a new access token
     * and a new refresh token on each refresh.
     */
    @Transactional
    public AuthResponse refreshToken(String refreshToken, String clientIp) {
        if (!tokenService.validateToken(refreshToken)) {
            throw new AuthenticationException("Invalid or expired refresh token", "INVALID_TOKEN");
        }

        String username = tokenService.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("User not found", "USER_NOT_FOUND"));

        if (!user.isActive()) {
            throw new AuthenticationException("Account is not active", "ACCOUNT_INACTIVE");
        }

        // ABAC check
        if (!lightAbacService.isAccessAllowed(user.getId(), user.getUsername(), clientIp)) {
            throw new AccessDeniedException("Access denied based on security policy");
        }

        // Generate both new access and refresh tokens (token rotation)
        String newAccessToken = tokenService.generateAccessToken(user);
        String newRefreshToken = tokenService.generateRefreshToken(user);
        String newIdToken = tokenService.generateIdToken(user, "finnera-web");

        Set<String> roles = user.getRoles().stream()
                .filter(Role::isEnabled)
                .map(Role::getName)
                .collect(Collectors.toSet());

        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .build();

        return AuthResponse.builder()
                .success(true)
                .message("Token refreshed")
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .idToken(newIdToken)
                .tokenType("Bearer")
                .expiresIn(tokenService.getAccessTokenExpiration() / 1000)
                .user(userInfo)
                .build();
    }

    /**
     * Validate a token.
     */
    public boolean validateToken(String token) {
        return tokenService.validateToken(token);
    }
}
