package com.fyp.nadra.controller;

import com.fyp.nadra.dto.*;
import com.fyp.nadra.service.NadraVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/verify")
@RequiredArgsConstructor
@Tag(name = "NADRA Verification", description = "Mock NADRA CNIC and biometric verification APIs")
public class NadraVerificationController {

    private final NadraVerificationService verificationService;

    @PostMapping("/cnic")
    @Operation(summary = "Verify CNIC", description = "Verify a CNIC number against mock NADRA database")
    public ResponseEntity<ApiResponse<VerificationResponse>> verifyCnic(
            @Valid @RequestBody CnicVerificationRequest request) {
        log.info("Received CNIC verification request for: {}", request.getCnicNumber());
        VerificationResponse result = verificationService.verifyCnic(request);
        
        if (result.getStatus() == VerificationResponse.VerificationStatus.VERIFIED) {
            return ResponseEntity.ok(ApiResponse.success("CNIC verification completed", result));
        } else {
            return ResponseEntity.ok(ApiResponse.success("CNIC verification completed", result));
        }
    }

    @PostMapping("/biometric")
    @Operation(summary = "Verify Biometric", description = "Simulate biometric verification against NADRA records")
    public ResponseEntity<ApiResponse<VerificationResponse>> verifyBiometric(
            @Valid @RequestBody BiometricVerificationRequest request) {
        log.info("Received biometric verification request for CNIC: {}", request.getCnicNumber());
        VerificationResponse result = verificationService.verifyBiometric(request);
        return ResponseEntity.ok(ApiResponse.success("Biometric verification completed", result));
    }

    @GetMapping("/status/{requestId}")
    @Operation(summary = "Check Verification Status", description = "Get the status of a previous verification request")
    public ResponseEntity<ApiResponse<VerificationResponse>> getVerificationStatus(
            @PathVariable String requestId) {
        log.info("Checking verification status for request: {}", requestId);
        VerificationResponse result = verificationService.getVerificationStatus(requestId);
        
        if (result.getStatus() == VerificationResponse.VerificationStatus.NOT_FOUND) {
            return ResponseEntity.status(404)
                .body(ApiResponse.error("NOT_FOUND", "Verification request not found: " + requestId));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Check if NADRA mock service is running")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("NADRA Mock Service is running"));
    }
}
