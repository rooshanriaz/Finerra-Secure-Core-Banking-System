package com.fyp.kyc.controller;

import com.fyp.kyc.dto.ApiResponse;
import com.fyp.kyc.dto.DidResponse;
import com.fyp.kyc.service.FabricDIDService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/did")
@RequiredArgsConstructor
@Tag(name = "DID", description = "Decentralized Identity operations")
public class DidController {

    private final FabricDIDService fabricDIDService;

    @GetMapping("/{didId}")
    @Operation(summary = "Resolve DID", description = "Look up a DID document by ID")
    public ResponseEntity<ApiResponse<DidResponse>> resolveDid(@PathVariable String didId) {
        log.info("Resolving DID: {}", didId);
        DidResponse response = fabricDIDService.resolveDID(didId);
        if (response == null) {
            return ResponseEntity.status(404)
                .body(ApiResponse.error("DID_NOT_FOUND", "DID not found: " + didId));
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/verify-credential")
    @Operation(summary = "Verify Credential", description = "Verify a verifiable credential by its ID")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyCredential(
            @RequestBody Map<String, String> request) {
        String credentialId = request.get("credentialId");
        if (credentialId == null || credentialId.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("INVALID_REQUEST", "credentialId is required"));
        }

        log.info("Verifying credential: {}", credentialId);
        Map<String, Object> result = fabricDIDService.verifyCredential(credentialId);
        return ResponseEntity.ok(ApiResponse.success("Credential verification completed", result));
    }
}
