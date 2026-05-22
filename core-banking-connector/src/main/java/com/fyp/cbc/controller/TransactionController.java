package com.fyp.cbc.controller;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fyp.cbc.dto.request.TransactionRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.TransactionResponse;
import com.fyp.cbc.dto.response.TransactionResponse.JournalEntryResponse;
import com.fyp.cbc.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for transaction operations.
 * Provides endpoints for deposits, withdrawals, loan repayments, and journal entries.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction processing APIs")
public class TransactionController {
    
    private final TransactionService transactionService;
    
    /**
     * Deposit funds to a savings account.
     * POST /v1/savingsaccounts/{accountId}/transactions?command=deposit
     */
    @PostMapping("/savingsaccounts/{accountId}/transactions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Account transaction", 
               description = "Process account transaction (deposit/withdrawal)")
    public ResponseEntity<ApiResponse<TransactionResponse>> processAccountTransaction(
            @Parameter(description = "Account ID") @PathVariable Long accountId,
            @Parameter(description = "Command (deposit/withdrawal)") @RequestParam String command,
            @Valid @RequestBody TransactionRequest request) {
        
        ApiResponse<TransactionResponse> response;
        
        switch (command.toLowerCase()) {
            case "deposit":
                response = transactionService.deposit(accountId, request);
                break;
            case "withdrawal":
                response = transactionService.withdraw(accountId, request);
                break;
            default:
                return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_COMMAND", "Unknown command: " + command));
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Make a loan repayment.
     * POST /v1/loans/{loanId}/transactions?command=repayment
     */
    @PostMapping("/loans/{loanId}/transactions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Loan repayment", description = "Process loan repayment")
    public ResponseEntity<ApiResponse<TransactionResponse>> processLoanRepayment(
            @Parameter(description = "Loan ID") @PathVariable Long loanId,
            @Parameter(description = "Command (repayment)") @RequestParam String command,
            @Valid @RequestBody TransactionRequest request) {
        
        if (!"repayment".equalsIgnoreCase(command)) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error("INVALID_COMMAND", "Unknown command: " + command));
        }
        
        ApiResponse<TransactionResponse> response = transactionService.loanRepayment(loanId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Retrieve journal entries.
     * GET /v1/journalentries
     */
    @GetMapping("/journalentries")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get journal entries", description = "Retrieve journal entries (transaction logs)")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> getJournalEntries(
            @Parameter(description = "Specific transaction ID") @RequestParam(required = false) String transactionId,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") Integer offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") Integer limit,
            @Parameter(description = "Office ID filter") @RequestParam(required = false) Long officeId,
            @Parameter(description = "GL Account ID filter") @RequestParam(required = false) Long glAccountId,
            @Parameter(description = "Manual entries only") @RequestParam(required = false) Boolean manualEntriesOnly) {
        
        ApiResponse<JournalEntryResponse> response;
        
        if (transactionId != null && !transactionId.isBlank()) {
            response = transactionService.getTransactionDetails(transactionId);
        } else {
            response = transactionService.getJournalEntries(offset, limit, officeId, glAccountId, manualEntriesOnly);
        }
        
        return ResponseEntity.ok(response);
    }
}
