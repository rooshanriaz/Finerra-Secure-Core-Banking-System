package com.fyp.fraud.controller;

import com.fyp.fraud.dto.ApiResponse;
import com.fyp.fraud.dto.ThresholdUpdateRequest;
import com.fyp.fraud.entity.RiskThreshold;
import com.fyp.fraud.service.FraudAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/fraud/thresholds")
@RequiredArgsConstructor
public class ThresholdController {

    private final FraudAlertService alertService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RiskThreshold>>> getThresholds() {
        return ResponseEntity.ok(ApiResponse.success(alertService.getAllThresholds()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM')")
    public ResponseEntity<ApiResponse<RiskThreshold>> updateThreshold(
            @Valid @RequestBody ThresholdUpdateRequest request,
            Authentication authentication) {

        log.info("Threshold update: name={}, value={}, by={}",
            request.getThresholdName(), request.getValue(), authentication.getName());

        RiskThreshold threshold = alertService.upsertThreshold(
            request.getThresholdName(),
            request.getValue(),
            request.getDescription(),
            authentication.getName()
        );

        return ResponseEntity.ok(ApiResponse.success("Threshold updated", threshold));
    }
}
