package com.fyp.cbc.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

import com.fyp.cbc.dto.request.CreateLoanRequest;
import com.fyp.cbc.dto.request.RescheduleRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.LoanResponse;
import com.fyp.cbc.service.LoanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for loan management operations.
 * Provides endpoints for loan applications, approvals, disbursements, and rescheduling.
 */
@RestController
@RequestMapping("/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan management APIs")
public class LoanController {
    
    private final LoanService loanService;
    
    /**
     * Create a new loan application.
     * POST /v1/loans
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER') or hasRole('MANAGER') or hasRole('LOAN_OFFICER')")
    @Operation(summary = "Create loan", description = "Create a new loan application")
    public ResponseEntity<ApiResponse<LoanResponse>> createLoan(
            @Valid @RequestBody CreateLoanRequest request) {
        ApiResponse<LoanResponse> response = loanService.createLoan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Retrieve loan details by ID.
     * GET /v1/loans/{loanId}
     */
    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan", description = "Retrieve loan details by ID")
    public ResponseEntity<ApiResponse<LoanResponse>> getLoan(
            @Parameter(description = "Loan ID") @PathVariable Long loanId) {
        ApiResponse<LoanResponse> response = loanService.getLoan(loanId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update loan details.
     * PUT /v1/loans/{loanId}
     */
    @PutMapping("/{loanId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Update loan", description = "Update loan details")
    public ResponseEntity<ApiResponse<LoanResponse>> updateLoan(
            @Parameter(description = "Loan ID") @PathVariable Long loanId,
            @Valid @RequestBody CreateLoanRequest request) {
        ApiResponse<LoanResponse> response = loanService.updateLoan(loanId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Execute loan command (forward/approve/disburse/reject).
     * POST /v1/loans/{loanId}?command=forward|approve|disburse|reject
     */
    @PostMapping("/{loanId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('LOAN_OFFICER')")
    @Operation(summary = "Loan command", 
               description = "Execute a command on the loan (approve/disburse/reject)")
    public ResponseEntity<ApiResponse<Object>> executeCommand(
            @Parameter(description = "Loan ID") @PathVariable Long loanId,
            @Parameter(description = "Command (forward/approve/disburse/reject)") @RequestParam String command,
            @Parameter(description = "Date (format: dd MMMM yyyy)") @RequestParam String date,
            @Parameter(description = "Amount (optional)") @RequestParam(required = false) BigDecimal amount,
            @Parameter(description = "Note (optional)") @RequestParam(required = false) String note) {
        
        ApiResponse<Object> response;
        
        switch (command.toLowerCase()) {
            case "forward":
                response = loanService.forwardToBranchManager(loanId, note);
                break;
            case "approve":
                response = loanService.approveLoan(loanId, date, amount, note);
                break;
            case "disburse":
                response = loanService.disburseLoan(loanId, date, amount);
                break;
            case "reject":
                response = loanService.rejectLoan(loanId, date, note);
                break;
            default:
                return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_COMMAND", "Unknown command: " + command));
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Search loans.
     * GET /v1/loans
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('LOAN_OFFICER')")
    @Operation(summary = "Search loans", description = "Search loans with optional filters")
    public ResponseEntity<ApiResponse<List<LoanResponse>>> searchLoans(
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") Integer offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") Integer limit) {
        ApiResponse<List<LoanResponse>> response = loanService.searchLoans(query, offset, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * List loan products.
     * GET /v1/loans/products
     */
    @GetMapping("/products")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LOAN_OFFICER')")
    @Operation(summary = "Get loan products", description = "List loan products from core banking")
    public ResponseEntity<ApiResponse<List<Object>>> getLoanProducts() {
        ApiResponse<List<Object>> response = loanService.getLoanProducts();
        return ResponseEntity.ok(response);
    }

    /**
     * Create loan product.
     * POST /v1/loans/products
     */
    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LOAN_OFFICER')")
    @Operation(summary = "Create loan product", description = "Create a loan product in core banking")
    public ResponseEntity<ApiResponse<Object>> createLoanProduct(
            @RequestBody Map<String, Object> request) {
        ApiResponse<Object> response = loanService.createLoanProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get loan transactions.
     * GET /v1/loans/{loanId}/transactions
     */
    @GetMapping("/{loanId}/transactions")
    @Operation(summary = "Get loan transactions", description = "List loan transactions")
    public ResponseEntity<ApiResponse<List<Object>>> getLoanTransactions(
            @Parameter(description = "Loan ID") @PathVariable Long loanId) {
        ApiResponse<List<Object>> response = loanService.getLoanTransactions(loanId);
        return ResponseEntity.ok(response);
    }
}
