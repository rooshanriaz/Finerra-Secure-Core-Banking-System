package com.fyp.kyc.controller;

import com.fyp.kyc.dto.AmlScreeningResponse;
import com.fyp.kyc.dto.ApiResponse;
import com.fyp.kyc.entity.SanctionEntry;
import com.fyp.kyc.security.AesEncryptionService;
import com.fyp.kyc.service.AmlScreeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/aml")
@RequiredArgsConstructor
@Tag(name = "AML", description = "Anti-Money Laundering screening APIs")
public class AmlController {

    private final AmlScreeningService amlScreeningService;
    private final AesEncryptionService encryptionService;

    @PostMapping("/screen")
    @Operation(summary = "AML Screening", description = "Screen a person against sanctions lists")
    public ResponseEntity<ApiResponse<AmlScreeningResponse>> screenPerson(
            @RequestBody Map<String, String> request) {
        String fullName = request.get("fullName");
        String cnicNumber = request.get("cnicNumber");

        if (fullName == null || fullName.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("INVALID_REQUEST", "fullName is required"));
        }

        String cnicHash = cnicNumber != null ? encryptionService.hash(cnicNumber) : "";
        String refId = "AML-" + System.currentTimeMillis();

        log.info("AML screening requested for: {}", fullName);
        AmlScreeningResponse result = amlScreeningService.screenPerson(fullName, cnicHash, refId);
        return ResponseEntity.ok(ApiResponse.success("AML screening completed", result));
    }

    @GetMapping("/sanctions/search")
    @Operation(summary = "Search Sanctions List", description = "Search the sanctions list by name")
    public ResponseEntity<ApiResponse<List<SanctionEntry>>> searchSanctions(
            @RequestParam String name) {
        log.info("Sanctions search for: {}", name);
        List<SanctionEntry> results = amlScreeningService.searchSanctions(name);
        return ResponseEntity.ok(ApiResponse.success("Found " + results.size() + " entries", results));
    }
}
