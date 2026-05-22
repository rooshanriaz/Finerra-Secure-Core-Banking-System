package com.fyp.kyc.controller;

import com.fyp.kyc.dto.*;
import com.fyp.kyc.service.KycOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "KYC onboarding and verification APIs")
public class KycController {

    private final KycOrchestrationService kycOrchestrationService;

    @PostMapping("/onboard")
    @Operation(summary = "Full KYC Onboarding", 
               description = "Complete KYC flow: CNIC verify → AML screen → DID create → Fineract client")
    public ResponseEntity<ApiResponse<KycOnboardResponse>> onboardClient(
            @Valid @RequestBody KycOnboardRequest request,
            Authentication authentication) {
        String initiatedBy = authentication != null ? authentication.getName() : "system";
        log.info("KYC onboarding initiated by: {}", initiatedBy);

        KycOnboardResponse response = kycOrchestrationService.onboardClient(request, initiatedBy);

        if ("REJECTED".equals(response.getKycStatus()) || "FAILED".equals(response.getKycStatus())) {
            return ResponseEntity.ok(ApiResponse.success("KYC onboarding " + response.getKycStatus().toLowerCase(), response));
        }
        return ResponseEntity.ok(ApiResponse.success("KYC onboarding completed successfully", response));
    }

    @GetMapping("/{referenceId}/status")
    @Operation(summary = "Get KYC Status", description = "Check KYC verification status by reference ID")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getKycStatus(@PathVariable String referenceId) {
        log.info("Getting KYC status for: {}", referenceId);
        KycStatusResponse status = kycOrchestrationService.getKycStatus(referenceId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/client/{clientId}/status")
    @Operation(summary = "Get KYC Status by Client ID", description = "Check KYC status by Fineract client ID")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getKycStatusByClientId(@PathVariable Long clientId) {
        log.info("Getting KYC status for client: {}", clientId);
        KycStatusResponse status = kycOrchestrationService.getKycStatusByClientId(clientId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/verify-cnic")
    @Operation(summary = "Verify CNIC Only", description = "Standalone CNIC verification without full onboarding")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyCnicOnly(
            @Valid @RequestBody KycOnboardRequest request) {
        log.info("Standalone CNIC verification requested");
        Map<String, Object> result = kycOrchestrationService.verifyCnicOnly(request);
        return ResponseEntity.ok(ApiResponse.success("CNIC verification completed", result));
    }
}
