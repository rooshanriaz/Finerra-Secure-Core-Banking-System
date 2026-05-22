package com.fyp.auth.controller;

import com.fyp.auth.dto.request.MfaVerifyRequest;
import com.fyp.auth.dto.response.ApiResponse;
import com.fyp.auth.dto.response.AuthResponse;
import com.fyp.auth.dto.response.MfaSetupResponse;
import com.fyp.auth.dto.response.MfaStatusResponse;
import com.fyp.auth.entity.User;
import com.fyp.auth.repository.UserRepository;
import com.fyp.auth.service.AuthenticationService;
import com.fyp.auth.service.BearerIdentityService;
import com.fyp.auth.service.TotpService;
import com.fyp.auth.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Multi-Factor Authentication operations.
 * Handles TOTP setup, verification during login, and MFA management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final AuthenticationService authenticationService;
    private final TotpService totpService;
    private final BearerIdentityService bearerIdentityService;
    private final UserRepository userRepository;

    /**
     * Verify TOTP code to complete MFA login.
     * POST /api/v1/auth/mfa/verify
     * Does NOT require Bearer auth — uses the temporary mfaToken.
     */
    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("MFA verification attempt from IP: {}", clientIp);

        AuthResponse response = authenticationService.verifyMfaAndLogin(
                request.getMfaToken(), request.getCode(), clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * Start MFA setup — generate TOTP secret and QR code URI.
     * GET /api/v1/auth/mfa/setup
     * Requires Bearer auth.
     */
    @GetMapping("/setup")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa(
            @RequestHeader("Authorization") String authHeader) {

        User user = extractUser(authHeader);

        if (user.isMfaEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("MFA is already enabled. Disable it first to reconfigure."));
        }

        String secret = totpService.generateSecret();
        String qrCodeUri = totpService.getQrCodeUri(secret, user.getUsername());

        // Temporarily store the secret (not yet confirmed)
        user.setMfaSecret(secret);
        userRepository.save(user);

        MfaSetupResponse setupResponse = MfaSetupResponse.builder()
                .secret(secret)
                .qrCodeUri(qrCodeUri)
                .issuer("Finnera Banking")
                .build();

        return ResponseEntity.ok(ApiResponse.success("Scan the QR code with your authenticator app", setupResponse));
    }

    /**
     * Confirm MFA setup by verifying the first TOTP code.
     * POST /api/v1/auth/mfa/confirm-setup
     * Requires Bearer auth.
     */
    @PostMapping("/confirm-setup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmSetup(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        User user = extractUser(authHeader);

        String code = request.get("code");
        if (code == null || code.length() != 6) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("A valid 6-digit code is required"));
        }

        if (user.getMfaSecret() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("MFA setup not started. Call /mfa/setup first."));
        }

        if (!totpService.verifyCode(user.getMfaSecret(), code)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid verification code. Please try again."));
        }

        user.setMfaEnabled(true);
        userRepository.save(user);

        log.info("MFA enabled for user: {}", user.getUsername());

        return ResponseEntity.ok(ApiResponse.success("MFA has been enabled successfully",
                Map.of("mfaEnabled", true, "username", user.getUsername())));
    }

    /**
     * Disable MFA (requires current TOTP code for security).
     * POST /api/v1/auth/mfa/disable
     * Requires Bearer auth.
     */
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disableMfa(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        User user = extractUser(authHeader);

        if (!user.isMfaEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("MFA is not enabled"));
        }

        String code = request.get("code");
        if (code == null || !totpService.verifyCode(user.getMfaSecret(), code)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid verification code"));
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaVerifiedAt(null);
        userRepository.save(user);

        log.info("MFA disabled for user: {}", user.getUsername());

        return ResponseEntity.ok(ApiResponse.success("MFA has been disabled"));
    }

    /**
     * Verify TOTP code for the currently authenticated user (post-login MFA check).
     * POST /api/v1/auth/mfa/verify-totp
     * Requires Bearer auth (Keycloak or auth-service token). Does NOT swap tokens —
     * it simply validates the TOTP code so the frontend can finalize the session.
     */
    @PostMapping("/verify-totp")
    public ResponseEntity<ApiResponse<Boolean>> verifyTotp(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        User user = extractUser(authHeader);
        String code = request.get("code");

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("A verification code is required"));
        }

        String cleanCode = code.replaceAll("\\s+", "");
        if (cleanCode.length() != 6) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("A valid 6-digit code is required"));
        }

        if (!user.isMfaEnabled() || user.getMfaSecret() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("MFA is not enabled for this account"));
        }

        if (!totpService.verifyCode(user.getMfaSecret(), cleanCode)) {
            log.warn("Invalid TOTP code during session verification for user: {}", user.getUsername());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Invalid verification code"));
        }

        log.info("TOTP session verification successful for user: {}", user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Verification successful", true));
    }

    /**
     * Get current MFA and DID status.
     * GET /api/v1/auth/mfa/status
     * Requires Bearer auth.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> getMfaStatus(
            @RequestHeader("Authorization") String authHeader) {

        User user = extractUser(authHeader);

        MfaStatusResponse status = MfaStatusResponse.builder()
                .mfaEnabled(user.isMfaEnabled())
                .didLinked(user.getDidIdentifier() != null && !user.getDidIdentifier().isBlank())
                .didIdentifier(user.getDidIdentifier())
                .build();

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    private User extractUser(String authHeader) {
        return bearerIdentityService.getUserForBearerToken(authHeader);
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }
}
