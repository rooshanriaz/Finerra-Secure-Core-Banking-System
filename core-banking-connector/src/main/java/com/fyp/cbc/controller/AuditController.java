package com.fyp.cbc.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.AuditResponse;
import com.fyp.cbc.dto.response.AuditResponse.AuditSearchCriteria;
import com.fyp.cbc.service.AuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for audit and reporting operations.
 * Provides endpoints for audit logs, compliance monitoring, and report generation.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Audits & Reports", description = "Compliance, monitoring, and reporting APIs")
public class AuditController {
    
    private final AuditService auditService;
    
    /**
     * Search audit logs.
     * GET /v1/audits
     */
    @GetMapping("/audits")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search audits", description = "Search audit logs with filters")
    public ResponseEntity<ApiResponse<AuditResponse>> searchAudits(
            @Parameter(description = "Action name filter") @RequestParam(required = false) String actionName,
            @Parameter(description = "Entity name filter") @RequestParam(required = false) String entityName,
            @Parameter(description = "Resource ID filter") @RequestParam(required = false) Long resourceId,
            @Parameter(description = "Maker ID filter") @RequestParam(required = false) String makerId,
            @Parameter(description = "From date (yyyy-MM-dd HH:mm:ss)") @RequestParam(required = false) String fromDate,
            @Parameter(description = "To date (yyyy-MM-dd HH:mm:ss)") @RequestParam(required = false) String toDate,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") Integer offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") Integer limit) {
        
        AuditSearchCriteria criteria = AuditSearchCriteria.builder()
            .actionName(actionName)
            .entityName(entityName)
            .resourceId(resourceId)
            .makerId(makerId)
            .makerDateTimeFrom(fromDate)
            .makerDateTimeTo(toDate)
            .offset(offset)
            .limit(limit)
            .orderBy("madeOnDate")
            .sortOrder("DESC")
            .build();
        
        ApiResponse<AuditResponse> response = auditService.searchAudits(criteria);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Retrieve specific audit entry by ID.
     * GET /v1/audits/{auditId}
     */
    @GetMapping("/audits/{auditId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit", description = "Retrieve specific audit entry by ID")
    public ResponseEntity<ApiResponse<AuditResponse.AuditData>> getAudit(
            @Parameter(description = "Audit ID") @PathVariable Long auditId) {
        ApiResponse<AuditResponse.AuditData> response = auditService.getAudit(auditId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * List available reports.
     * GET /v1/reports
     */
    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List reports", description = "List available reports")
    public ResponseEntity<ApiResponse<List<Object>>> listReports() {
        ApiResponse<List<Object>> response = auditService.listReports();
        return ResponseEntity.ok(response);
    }
    
    /**
     * Run a specific report.
     * GET /v1/reports/{reportName}
     */
    @GetMapping("/reports/{reportName}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Run report", description = "Run a specific report")
    public ResponseEntity<ApiResponse<Object>> runReport(
            @Parameter(description = "Report name") @PathVariable String reportName,
            @Parameter(description = "Report parameters") @RequestParam(required = false) Map<String, Object> parameters) {
        ApiResponse<Object> response = auditService.runReport(reportName, parameters);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Create a custom report template.
     * POST /v1/reports
     */
    @PostMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create report", description = "Create a custom report template")
    public ResponseEntity<ApiResponse<Object>> createReport(@RequestBody Object reportDefinition) {
        ApiResponse<Object> response = auditService.createReport(reportDefinition);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
