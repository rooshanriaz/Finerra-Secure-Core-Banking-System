package com.fyp.txn.controller;

import com.fyp.txn.dto.ApiResponse;
import com.fyp.txn.dto.TransactionApprovalRequestPayload;
import com.fyp.txn.dto.TransactionApprovalRequestResponse;
import com.fyp.txn.dto.TransactionLimitConfigRequest;
import com.fyp.txn.dto.TransactionLimitConfigResponse;
import com.fyp.txn.dto.TransactionRequest;
import com.fyp.txn.dto.TransactionResponse;
import com.fyp.txn.service.TransactionApprovalWorkflowService;
import com.fyp.txn.service.TransactionLimitConfigService;
import com.fyp.txn.service.TransactionProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Transaction Controller.
 * Handles deposit, withdrawal, loan repayment, and transaction queries.
 */
@Slf4j
@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction Operations", description = "Secure transaction processing with validation and audit")
public class TransactionController {

    private final TransactionProcessingService processingService;
    private final TransactionLimitConfigService limitConfigService;
    private final TransactionApprovalWorkflowService approvalWorkflowService;

    /**
     * Execute a deposit.
     */
    @PostMapping("/savings/{accountId}/deposit")
    @Operation(summary = "Execute deposit", description = "Validates and processes a deposit to a savings account")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody TransactionRequest request) {
        log.info("Deposit request: accountId={}, amount={}", accountId, request.getTransactionAmount());

        TransactionResponse response = processingService.processDeposit(accountId, request);
        
        if ("REJECTED".equals(response.getStatus())) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_FAILED", response.getValidation().getMessage()));
        }
        if ("FAILED".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("PROCESSING_FAILED", "Transaction failed during processing"));
        }

        return ResponseEntity.ok(ApiResponse.success("Deposit processed successfully", response));
    }

    /**
     * Execute a withdrawal.
     */
    @PostMapping("/savings/{accountId}/withdrawal")
    @Operation(summary = "Execute withdrawal", description = "Validates and processes a withdrawal from a savings account")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdrawal(
            @PathVariable Long accountId,
            @Valid @RequestBody TransactionRequest request) {
        log.info("Withdrawal request: accountId={}, amount={}", accountId, request.getTransactionAmount());

        TransactionResponse response = processingService.processWithdrawal(accountId, request);

        if ("REJECTED".equals(response.getStatus())) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_FAILED", response.getValidation().getMessage()));
        }
        if ("FAILED".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("PROCESSING_FAILED", "Transaction failed during processing"));
        }

        return ResponseEntity.ok(ApiResponse.success("Withdrawal processed successfully", response));
    }

    /**
     * Execute a loan repayment.
     */
    @PostMapping("/loans/{loanId}/repayment")
    @Operation(summary = "Loan repayment", description = "Validates and processes a loan repayment")
    public ResponseEntity<ApiResponse<TransactionResponse>> loanRepayment(
            @PathVariable Long loanId,
            @Valid @RequestBody TransactionRequest request) {
        log.info("Loan repayment request: loanId={}, amount={}", loanId, request.getTransactionAmount());

        TransactionResponse response = processingService.processLoanRepayment(loanId, request);

        if ("REJECTED".equals(response.getStatus())) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_FAILED", response.getValidation().getMessage()));
        }
        if ("FAILED".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("PROCESSING_FAILED", "Transaction failed during processing"));
        }

        return ResponseEntity.ok(ApiResponse.success("Loan repayment processed successfully", response));
    }

    /**
     * Get a transaction by ID.
     */
    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction", description = "Retrieve transaction details by transaction ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable String transactionId) {
        TransactionResponse response = processingService.getTransaction(transactionId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get account transaction history.
     */
    @GetMapping("/savings/{accountId}/history")
    @Operation(summary = "Account transaction history", description = "Retrieve all transactions for a savings account")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getAccountHistory(
            @PathVariable Long accountId) {
        List<TransactionResponse> history = processingService.getAccountTransactions(accountId);
        return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved", history));
    }

    /**
     * Recent transactions across all accounts (for admin / branch dashboards).
     */
    @GetMapping("/recent")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Recent transactions (aggregate)", description = "Latest transactions across all savings/loan activity, newest first")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getRecentTransactions(
            @RequestParam(defaultValue = "500") int limit) {
        List<TransactionResponse> rows = processingService.getRecentTransactions(limit);
        return ResponseEntity.ok(ApiResponse.success("Recent transactions retrieved", rows));
    }

    @PostMapping("/requests")
    @Operation(summary = "Create transaction request", description = "Create a pending transaction request for manager/admin approval")
    public ResponseEntity<ApiResponse<TransactionApprovalRequestResponse>> createTransactionRequest(
            @Valid @RequestBody TransactionApprovalRequestPayload request,
            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "unknown";
        TransactionApprovalRequestResponse created = approvalWorkflowService.createRequest(request, actor);
        return ResponseEntity.ok(ApiResponse.success("Transaction request submitted", created));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "List transaction requests", description = "List pending/decided transaction requests for review")
    public ResponseEntity<ApiResponse<List<TransactionApprovalRequestResponse>>> listTransactionRequests(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Transaction requests fetched",
                approvalWorkflowService.listRequests(status)));
    }

    @PostMapping("/requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Approve transaction request", description = "Approve and execute a pending request")
    public ResponseEntity<ApiResponse<TransactionApprovalRequestResponse>> approveTransactionRequest(
            @PathVariable String requestId,
            @RequestParam(required = false) String note,
            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "unknown";
        TransactionApprovalRequestResponse response = approvalWorkflowService.approveAndExecute(requestId, actor, note);
        return ResponseEntity.ok(ApiResponse.success("Transaction request approved", response));
    }

    @PostMapping("/requests/{requestId}/decline")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Decline transaction request", description = "Decline a pending request")
    public ResponseEntity<ApiResponse<TransactionApprovalRequestResponse>> declineTransactionRequest(
            @PathVariable String requestId,
            @RequestParam(required = false) String note,
            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "unknown";
        TransactionApprovalRequestResponse response = approvalWorkflowService.decline(requestId, actor, note);
        return ResponseEntity.ok(ApiResponse.success("Transaction request declined", response));
    }

    @GetMapping("/config/limits")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get transaction limits", description = "Returns runtime transaction validation thresholds")
    public ResponseEntity<ApiResponse<TransactionLimitConfigResponse>> getLimits() {
        return ResponseEntity.ok(ApiResponse.success("Transaction limits fetched", limitConfigService.getCurrent()));
    }

    @PutMapping("/config/limits")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update transaction limits", description = "Updates validation thresholds for transaction processing")
    public ResponseEntity<ApiResponse<TransactionLimitConfigResponse>> updateLimits(
            @Valid @RequestBody TransactionLimitConfigRequest request,
            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "unknown";
        TransactionLimitConfigResponse updated = limitConfigService.update(request, actor);
        return ResponseEntity.ok(ApiResponse.success("Transaction limits updated successfully", updated));
    }
}
