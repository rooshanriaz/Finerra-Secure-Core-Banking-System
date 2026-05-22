package com.fyp.fraud.controller;

import com.fyp.fraud.dto.ApiResponse;
import com.fyp.fraud.dto.FraudAlertResponse;
import com.fyp.fraud.entity.FraudAlert.AlertStatus;
import com.fyp.fraud.service.FraudAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/fraud/alerts")
@RequiredArgsConstructor
public class FraudAlertController {

    private final FraudAlertService alertService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FraudAlertResponse>>> getRecentAlerts() {
        return ResponseEntity.ok(ApiResponse.success(alertService.getRecentAlerts()));
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<ApiResponse<FraudAlertResponse>> getAlert(@PathVariable String alertId) {
        return alertService.getAlert(alertId)
            .map(a -> ResponseEntity.ok(ApiResponse.success(a)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<FraudAlertResponse>>> getByStatus(
            @PathVariable String status) {
        AlertStatus alertStatus = AlertStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(alertService.getAlertsByStatus(alertStatus)));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<List<FraudAlertResponse>>> getByAccount(
            @PathVariable Long accountId) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getAlertsByAccount(accountId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(alertService.getAlertStats()));
    }

    @PutMapping("/{alertId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FraudAlertResponse>> updateStatus(
            @PathVariable String alertId,
            @RequestParam String status,
            @RequestParam(required = false) String note,
            Authentication authentication) {

        AlertStatus newStatus = AlertStatus.valueOf(status.toUpperCase());
        String resolvedBy = authentication.getName();

        return alertService.updateAlertStatus(alertId, newStatus, resolvedBy, note)
            .map(a -> ResponseEntity.ok(ApiResponse.success("Alert updated", a)))
            .orElse(ResponseEntity.notFound().build());
    }
}
