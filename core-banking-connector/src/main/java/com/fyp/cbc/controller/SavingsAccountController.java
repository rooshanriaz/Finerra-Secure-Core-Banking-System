package com.fyp.cbc.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fyp.cbc.dto.request.CreateSavingsAccountRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.SavingsAccountResponse;
import com.fyp.cbc.service.SavingsAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for savings account management operations.
 * Provides endpoints for account management.
 */
@RestController
@RequestMapping("/v1/savingsaccounts")
@RequiredArgsConstructor
@Tag(name = "Savings Accounts", description = "Account management APIs")
public class SavingsAccountController {
    
    private final SavingsAccountService savingsAccountService;
    
    /**
     * Create a new savings account for a client.
     * POST /v1/savingsaccounts
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Create savings account", 
               description = "Create a new savings account for a client")
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> createSavingsAccount(
            @Valid @RequestBody CreateSavingsAccountRequest request) {
        ApiResponse<SavingsAccountResponse> response = savingsAccountService.createSavingsAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Retrieve savings account details by ID.
     * GET /v1/savingsaccounts/{accountId}
     */
    @GetMapping("/{accountId}")
    @Operation(summary = "Get savings account", 
               description = "Retrieve savings account details by ID")
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> getSavingsAccount(
            @Parameter(description = "Account ID") @PathVariable Long accountId) {
        ApiResponse<SavingsAccountResponse> response = savingsAccountService.getSavingsAccount(accountId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update savings account details.
     * PUT /v1/savingsaccounts/{accountId}
     */
    @PutMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Update savings account", 
               description = "Update savings account details")
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> updateSavingsAccount(
            @Parameter(description = "Account ID") @PathVariable Long accountId,
            @Valid @RequestBody CreateSavingsAccountRequest request) {
        ApiResponse<SavingsAccountResponse> response = savingsAccountService.updateSavingsAccount(
            accountId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Search savings accounts.
     * GET /v1/savingsaccounts
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search savings accounts", 
               description = "Search savings accounts with optional filters")
    public ResponseEntity<ApiResponse<List<SavingsAccountResponse>>> searchSavingsAccounts(
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") Integer offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") Integer limit) {
        ApiResponse<List<SavingsAccountResponse>> response = savingsAccountService.searchSavingsAccounts(
            query, offset, limit);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Approve a savings account.
     * POST /v1/savingsaccounts/{accountId}?command=approve
     */
    @PostMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Account command", 
               description = "Execute a command on the savings account (approve/activate)")
    public ResponseEntity<ApiResponse<Object>> executeCommand(
            @Parameter(description = "Account ID") @PathVariable Long accountId,
            @Parameter(description = "Command (approve/activate)") @RequestParam String command,
            @Parameter(description = "Date (format: dd MMMM yyyy)") @RequestParam String date) {
        ApiResponse<Object> response;
        
        switch (command.toLowerCase()) {
            case "approve":
                response = savingsAccountService.approveSavingsAccount(accountId, date);
                break;
            case "activate":
                response = savingsAccountService.activateSavingsAccount(accountId, date);
                break;
            default:
                return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_COMMAND", "Unknown command: " + command));
        }
        
        return ResponseEntity.ok(response);
    }
}
