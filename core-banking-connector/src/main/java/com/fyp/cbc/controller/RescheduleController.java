package com.fyp.cbc.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fyp.cbc.dto.request.RescheduleRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.service.LoanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for loan reschedule operations.
 * Provides endpoints for managing loan reschedule requests.
 */
@RestController
@RequestMapping("/v1/rescheduleloans")
@RequiredArgsConstructor
@Tag(name = "Reschedule Loans", description = "Loan reschedule management APIs")
public class RescheduleController {
    
    private final LoanService loanService;
    
    /**
     * Get all reschedule requests.
     * GET /v1/rescheduleloans
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get reschedule requests", 
               description = "Retrieve all loan reschedule requests")
    public ResponseEntity<ApiResponse<List<Object>>> getRescheduleRequests() {
        ApiResponse<List<Object>> response = loanService.getRescheduleRequests();
        return ResponseEntity.ok(response);
    }
    
    /**
     * Create a reschedule request.
     * POST /v1/rescheduleloans
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create reschedule request", 
               description = "Create a new loan reschedule request")
    public ResponseEntity<ApiResponse<Object>> createRescheduleRequest(
            @Valid @RequestBody RescheduleRequest request) {
        ApiResponse<Object> response = loanService.createRescheduleRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get reschedule request by ID.
     * GET /v1/rescheduleloans/{scheduleId}
     */
    @GetMapping("/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get reschedule request", 
               description = "Retrieve loan reschedule request by ID")
    public ResponseEntity<ApiResponse<Object>> getRescheduleRequest(
            @Parameter(description = "Reschedule request ID") @PathVariable Long scheduleId) {
        ApiResponse<Object> response = loanService.getRescheduleRequest(scheduleId);
        return ResponseEntity.ok(response);
    }
}
