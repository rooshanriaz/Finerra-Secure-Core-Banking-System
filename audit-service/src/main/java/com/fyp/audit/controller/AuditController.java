package com.fyp.audit.controller;

import com.fyp.audit.dto.ApiResponse;
import com.fyp.audit.dto.AuditRecordRequest;
import com.fyp.audit.dto.AuditRecordResponse;
import com.fyp.audit.dto.IntegrityCheckResponse;
import com.fyp.audit.service.AuditProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Audit Controller.
 * Manages audit records, integrity verification, and blockchain anchoring.
 */
@Slf4j
@RestController
@RequestMapping("/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Operations", description = "Blockchain-anchored audit trail management")
public class AuditController {

    private final AuditProcessingService auditService;

    /**
     * Record a transaction audit entry (called by transaction-service).
     */
    @PostMapping("/record")
    @Operation(summary = "Record audit entry", description = "Creates an audit record with SHA-256 hash and blockchain anchoring")
    public ResponseEntity<ApiResponse<AuditRecordResponse>> recordAudit(
            @Valid @RequestBody AuditRecordRequest request) {
        log.info("Audit record request: txnId={}", request.getTransactionId());
        AuditRecordResponse response = auditService.recordAudit(request);
        return ResponseEntity.ok(ApiResponse.success("Audit record created", response));
    }

    /**
     * Get audit record by audit ID.
     */
    @GetMapping("/{auditId}")
    @Operation(summary = "Get audit record", description = "Retrieve audit record by audit ID")
    public ResponseEntity<ApiResponse<AuditRecordResponse>> getAuditRecord(
            @PathVariable String auditId) {
        AuditRecordResponse response = auditService.getAuditRecord(auditId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get audit record by transaction ID.
     */
    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get audit by transaction", description = "Retrieve audit record by transaction ID")
    public ResponseEntity<ApiResponse<AuditRecordResponse>> getAuditByTransaction(
            @PathVariable String transactionId) {
        AuditRecordResponse response = auditService.getAuditByTransactionId(transactionId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get audit records for an account.
     */
    @GetMapping("/account/{accountId}")
    @Operation(summary = "Account audit history", description = "Get all audit records for a specific account")
    public ResponseEntity<ApiResponse<List<AuditRecordResponse>>> getAccountAudits(
            @PathVariable Long accountId) {
        List<AuditRecordResponse> records = auditService.getAuditsByAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success("Account audit records retrieved", records));
    }

    /**
     * Get audit records by user.
     */
    @GetMapping("/user/{username}")
    @Operation(summary = "User audit history", description = "Get all audit records initiated by a specific user")
    public ResponseEntity<ApiResponse<List<AuditRecordResponse>>> getUserAudits(
            @PathVariable String username) {
        List<AuditRecordResponse> records = auditService.getAuditsByUser(username);
        return ResponseEntity.ok(ApiResponse.success("User audit records retrieved", records));
    }

    /**
     * Get audit records by date range.
     */
    @GetMapping("/range")
    @Operation(summary = "Audit records by date range", description = "Get audit records within a date range")
    public ResponseEntity<ApiResponse<List<AuditRecordResponse>>> getAuditsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<AuditRecordResponse> records = auditService.getAuditsByDateRange(from, to);
        return ResponseEntity.ok(ApiResponse.success("Audit records retrieved", records));
    }

    /**
     * Get all recent audit records.
     */
    @GetMapping("/recent")
    @Operation(summary = "Recent audits", description = "Get all recent audit records")
    public ResponseEntity<ApiResponse<List<AuditRecordResponse>>> getRecentAudits() {
        List<AuditRecordResponse> records = auditService.getRecentAudits();
        return ResponseEntity.ok(ApiResponse.success("Recent audit records", records));
    }

    /**
     * Verify integrity of a single audit record.
     */
    @GetMapping("/{auditId}/verify")
    @Operation(summary = "Verify audit integrity", description = "Verify data integrity of a single audit record against blockchain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyIntegrity(
            @PathVariable String auditId) {
        Map<String, Object> result = auditService.verifyAuditIntegrity(auditId);
        return ResponseEntity.ok(ApiResponse.success("Integrity check completed", result));
    }

    /**
     * Run full integrity check on all audit records.
     */
    @PostMapping("/integrity-check")
    @Operation(summary = "Full integrity check", description = "Verify data integrity of all audit records")
    public ResponseEntity<ApiResponse<IntegrityCheckResponse>> runIntegrityCheck() {
        log.info("Full integrity check requested");
        IntegrityCheckResponse result = auditService.runFullIntegrityCheck();
        String message = result.isAllIntact() ? "All records integrity verified" : "Integrity violations detected!";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    /**
     * Export compliance report in CSV or PDF.
     */
    @GetMapping("/reports/export")
    @Operation(summary = "Export compliance report", description = "Exports audit records as CSV or PDF for compliance review")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<AuditRecordResponse> records = auditService.getAuditsForReport(from, to);
        String normalized = format == null ? "csv" : format.trim().toLowerCase();
        String timestamp = java.time.LocalDate.now().toString();

        if ("pdf".equals(normalized)) {
            byte[] body = auditService.generatePdfReport(records, from, to);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compliance-report-" + timestamp + ".pdf")
                    .body(body);
        }

        byte[] body = auditService.generateCsvReport(records);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compliance-report-" + timestamp + ".csv")
                .body(body);
    }
}
