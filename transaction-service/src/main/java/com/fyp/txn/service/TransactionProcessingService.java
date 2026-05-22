package com.fyp.txn.service;

import com.fyp.txn.dto.TransactionRequest;
import com.fyp.txn.dto.TransactionResponse;
import com.fyp.txn.dto.TransactionResponse.ValidationResult;
import com.fyp.txn.entity.TransactionRecord;
import com.fyp.txn.entity.TransactionRecord.TransactionStatus;
import com.fyp.txn.entity.TransactionRecord.TransactionType;
import com.fyp.txn.repository.TransactionRecordRepository;
import com.fyp.txn.security.InputSanitizer;
import com.fyp.txn.service.FraudScoringClient.FraudScoringResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core transaction processing service.
 * Orchestrates validation -> CBC execution -> audit recording.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessingService {

    private final TransactionValidationService validationService;
    private final CbcClientService cbcClientService;
    private final AuditClientService auditClientService;
    private final FraudScoringClient fraudScoringClient;
    private final TransactionRecordRepository repository;
    private final InputSanitizer inputSanitizer;

    /**
     * Process a deposit transaction.
     */
    public TransactionResponse processDeposit(Long accountId, TransactionRequest request) {
        String txnId = generateTransactionId();
        log.info("Processing deposit: txnId={}, accountId={}, amount={}", txnId, accountId, request.getTransactionAmount());

        // Sanitize inputs
        String sanitizedNote = inputSanitizer.sanitize(request.getNote());
        String sanitizedDate = inputSanitizer.sanitize(request.getTransactionDate());

        // Validate
        ValidationResult validation = validationService.validateDeposit(accountId, request.getTransactionAmount());
        if (!validation.isValid()) {
            log.warn("Deposit validation failed: {}", validation.getMessage());
            return buildRejectedResponse(txnId, accountId, null, TransactionType.DEPOSIT, request, validation);
        }

        // Fraud risk scoring
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        FraudScoringResult fraudResult = fraudScoringClient.scoreTransaction(
            txnId, accountId, null, "DEPOSIT", request.getTransactionAmount(), username);

        if ("BLOCK".equals(fraudResult.recommendation())) {
            log.warn("Transaction BLOCKED by fraud detection: txnId={}, score={}", txnId, fraudResult.riskScore());
            return buildFraudBlockedResponse(txnId, accountId, null, TransactionType.DEPOSIT, request, validation, fraudResult);
        }

        // Create pending record
        TransactionRecord record = createRecord(txnId, accountId, null, 
            TransactionType.DEPOSIT, request.getTransactionAmount(), sanitizedDate, sanitizedNote);
        applyFraudResult(record, fraudResult);

        // Execute via CBC
        try {
            record.setStatus(TransactionStatus.PROCESSING);
            repository.save(record);

            Map<String, Object> cbcResponse = cbcClientService.deposit(
                accountId, request.getTransactionAmount(), sanitizedDate, sanitizedNote);

            if (cbcResponse != null && !Boolean.FALSE.equals(cbcResponse.get("success"))) {
                record.setStatus(TransactionStatus.COMPLETED);
                String fineractTxnId = extractFineractTxnId(cbcResponse);
                record.setFineractTransactionId(fineractTxnId);
                repository.save(record);

                // Async audit recording
                auditClientService.recordTransactionAudit(record);

                log.info("Deposit completed: txnId={}, fineractTxnId={}", txnId, fineractTxnId);
                return buildSuccessResponse(record, validation);
            } else {
                record.setStatus(TransactionStatus.FAILED);
                record.setNote((sanitizedNote != null ? sanitizedNote : "") + " | CBC Error: " + cbcResponse);
                repository.save(record);
                return buildFailedResponse(record, "Transaction failed at core banking system", validation);
            }
        } catch (Exception e) {
            record.setStatus(TransactionStatus.FAILED);
            repository.save(record);
            log.error("Deposit failed: txnId={}, error={}", txnId, e.getMessage());
            return buildFailedResponse(record, "Transaction processing error: " + e.getMessage(), validation);
        }
    }

    /**
     * Process a withdrawal transaction.
     */
    public TransactionResponse processWithdrawal(Long accountId, TransactionRequest request) {
        String txnId = generateTransactionId();
        log.info("Processing withdrawal: txnId={}, accountId={}, amount={}", txnId, accountId, request.getTransactionAmount());

        String sanitizedNote = inputSanitizer.sanitize(request.getNote());
        String sanitizedDate = inputSanitizer.sanitize(request.getTransactionDate());

        ValidationResult validation = validationService.validateWithdrawal(accountId, request.getTransactionAmount());
        if (!validation.isValid()) {
            log.warn("Withdrawal validation failed: {}", validation.getMessage());
            return buildRejectedResponse(txnId, accountId, null, TransactionType.WITHDRAWAL, request, validation);
        }

        // Fraud risk scoring
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        FraudScoringResult fraudResult = fraudScoringClient.scoreTransaction(
            txnId, accountId, null, "WITHDRAWAL", request.getTransactionAmount(), username);

        if ("BLOCK".equals(fraudResult.recommendation())) {
            log.warn("Transaction BLOCKED by fraud detection: txnId={}, score={}", txnId, fraudResult.riskScore());
            return buildFraudBlockedResponse(txnId, accountId, null, TransactionType.WITHDRAWAL, request, validation, fraudResult);
        }

        TransactionRecord record = createRecord(txnId, accountId, null,
            TransactionType.WITHDRAWAL, request.getTransactionAmount(), sanitizedDate, sanitizedNote);
        applyFraudResult(record, fraudResult);

        try {
            record.setStatus(TransactionStatus.PROCESSING);
            repository.save(record);

            Map<String, Object> cbcResponse = cbcClientService.withdraw(
                accountId, request.getTransactionAmount(), sanitizedDate, sanitizedNote);

            if (cbcResponse != null && !Boolean.FALSE.equals(cbcResponse.get("success"))) {
                record.setStatus(TransactionStatus.COMPLETED);
                String fineractTxnId = extractFineractTxnId(cbcResponse);
                record.setFineractTransactionId(fineractTxnId);
                repository.save(record);

                auditClientService.recordTransactionAudit(record);

                log.info("Withdrawal completed: txnId={}", txnId);
                return buildSuccessResponse(record, validation);
            } else {
                record.setStatus(TransactionStatus.FAILED);
                repository.save(record);
                return buildFailedResponse(record, "Transaction failed at core banking system", validation);
            }
        } catch (Exception e) {
            record.setStatus(TransactionStatus.FAILED);
            repository.save(record);
            log.error("Withdrawal failed: txnId={}, error={}", txnId, e.getMessage());
            return buildFailedResponse(record, "Transaction processing error: " + e.getMessage(), validation);
        }
    }

    /**
     * Process a loan repayment.
     */
    public TransactionResponse processLoanRepayment(Long loanId, TransactionRequest request) {
        String txnId = generateTransactionId();
        log.info("Processing loan repayment: txnId={}, loanId={}, amount={}", txnId, loanId, request.getTransactionAmount());

        String sanitizedNote = inputSanitizer.sanitize(request.getNote());
        String sanitizedDate = inputSanitizer.sanitize(request.getTransactionDate());

        ValidationResult validation = validationService.validateLoanRepayment(loanId, request.getTransactionAmount());
        if (!validation.isValid()) {
            return buildRejectedResponse(txnId, null, loanId, TransactionType.LOAN_REPAYMENT, request, validation);
        }

        // Fraud risk scoring
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        FraudScoringResult fraudResult = fraudScoringClient.scoreTransaction(
            txnId, null, loanId, "LOAN_REPAYMENT", request.getTransactionAmount(), username);

        if ("BLOCK".equals(fraudResult.recommendation())) {
            log.warn("Transaction BLOCKED by fraud detection: txnId={}, score={}", txnId, fraudResult.riskScore());
            return buildFraudBlockedResponse(txnId, null, loanId, TransactionType.LOAN_REPAYMENT, request, validation, fraudResult);
        }

        TransactionRecord record = createRecord(txnId, null, loanId,
            TransactionType.LOAN_REPAYMENT, request.getTransactionAmount(), sanitizedDate, sanitizedNote);
        applyFraudResult(record, fraudResult);

        try {
            record.setStatus(TransactionStatus.PROCESSING);
            repository.save(record);

            Map<String, Object> cbcResponse = cbcClientService.loanRepayment(
                loanId, request.getTransactionAmount(), sanitizedDate, sanitizedNote);

            if (cbcResponse != null && !Boolean.FALSE.equals(cbcResponse.get("success"))) {
                record.setStatus(TransactionStatus.COMPLETED);
                String fineractTxnId = extractFineractTxnId(cbcResponse);
                record.setFineractTransactionId(fineractTxnId);
                repository.save(record);

                auditClientService.recordTransactionAudit(record);

                log.info("Loan repayment completed: txnId={}", txnId);
                return buildSuccessResponse(record, validation);
            } else {
                record.setStatus(TransactionStatus.FAILED);
                repository.save(record);
                return buildFailedResponse(record, "Loan repayment failed at core banking system", validation);
            }
        } catch (Exception e) {
            record.setStatus(TransactionStatus.FAILED);
            repository.save(record);
            log.error("Loan repayment failed: txnId={}, error={}", txnId, e.getMessage());
            return buildFailedResponse(record, "Transaction processing error: " + e.getMessage(), validation);
        }
    }

    /**
     * Get transaction by ID.
     */
    public TransactionResponse getTransaction(String transactionId) {
        return repository.findByTransactionId(transactionId)
            .map(this::mapToResponse)
            .orElse(null);
    }

    /**
     * Get transactions for an account.
     */
    public List<TransactionResponse> getAccountTransactions(Long accountId) {
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Recent transactions across all accounts (dashboard aggregate).
     */
    public List<TransactionResponse> getRecentTransactions(int limit) {
        int cap = Math.min(Math.max(limit, 1), 2000);
        return repository.findAll(PageRequest.of(0, cap, Sort.by(Sort.Direction.DESC, "createdAt")))
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private TransactionRecord createRecord(String txnId, Long accountId, Long loanId,
                                            TransactionType type, java.math.BigDecimal amount,
                                            String date, String note) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";

        TransactionRecord record = TransactionRecord.builder()
            .transactionId(txnId)
            .accountId(accountId)
            .loanId(loanId)
            .transactionType(type)
            .amount(amount)
            .transactionDate(date)
            .status(TransactionStatus.VALIDATED)
            .initiatedBy(username)
            .note(note)
            .auditStatus("PENDING")
            .build();
        return repository.save(record);
    }

    private String extractFineractTxnId(Map<String, Object> cbcResponse) {
        // CBC wraps response in ApiResponse with data field
        if (cbcResponse.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) cbcResponse.get("data");
            if (data != null && data.containsKey("resourceId")) {
                return String.valueOf(data.get("resourceId"));
            }
        }
        if (cbcResponse.containsKey("resourceId")) {
            return String.valueOf(cbcResponse.get("resourceId"));
        }
        return "fineract-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void applyFraudResult(TransactionRecord record, FraudScoringResult fraudResult) {
        record.setRiskScore(BigDecimal.valueOf(fraudResult.riskScore()));
        record.setRiskLevel(fraudResult.riskLevel());
        record.setFraudAlertId(fraudResult.alertId());
        repository.save(record);
    }

    private TransactionResponse buildFraudBlockedResponse(String txnId, Long accountId, Long loanId,
                                                           TransactionType type, TransactionRequest request,
                                                           ValidationResult validation, FraudScoringResult fraudResult) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        TransactionRecord record = TransactionRecord.builder()
            .transactionId(txnId)
            .accountId(accountId)
            .loanId(loanId)
            .transactionType(type)
            .amount(request.getTransactionAmount())
            .transactionDate(request.getTransactionDate())
            .status(TransactionStatus.FRAUD_BLOCKED)
            .riskScore(BigDecimal.valueOf(fraudResult.riskScore()))
            .riskLevel(fraudResult.riskLevel())
            .fraudAlertId(fraudResult.alertId())
            .note("Blocked by AI fraud detection: risk score " + fraudResult.riskScore())
            .auditStatus("FRAUD_BLOCKED")
            .initiatedBy(username)
            .build();
        repository.save(record);
        auditClientService.recordTransactionAudit(record);

        return TransactionResponse.builder()
            .transactionId(txnId)
            .accountId(accountId)
            .loanId(loanId)
            .transactionType(type.name())
            .amount(request.getTransactionAmount())
            .status("FRAUD_BLOCKED")
            .auditStatus("FRAUD_BLOCKED")
            .riskScore(fraudResult.riskScore())
            .riskLevel(fraudResult.riskLevel())
            .riskFactors(fraudResult.riskFactors())
            .fraudAlertId(fraudResult.alertId())
            .validation(validation)
            .build();
    }

    private TransactionResponse buildSuccessResponse(TransactionRecord record, ValidationResult validation) {
        return TransactionResponse.builder()
            .transactionId(record.getTransactionId())
            .accountId(record.getAccountId())
            .loanId(record.getLoanId())
            .transactionType(record.getTransactionType().name())
            .amount(record.getAmount())
            .transactionDate(record.getTransactionDate())
            .status(record.getStatus().name())
            .fineractTransactionId(record.getFineractTransactionId())
            .auditReference(record.getAuditReference())
            .auditStatus(record.getAuditStatus())
            .processedAt(record.getUpdatedAt())
            .riskScore(record.getRiskScore() != null ? record.getRiskScore().doubleValue() : null)
            .riskLevel(record.getRiskLevel())
            .fraudAlertId(record.getFraudAlertId())
            .validation(validation)
            .build();
    }

    private TransactionResponse buildRejectedResponse(String txnId, Long accountId, Long loanId,
                                                       TransactionType type, TransactionRequest request,
                                                       ValidationResult validation) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        TransactionRecord record = TransactionRecord.builder()
            .transactionId(txnId)
            .accountId(accountId)
            .loanId(loanId)
            .transactionType(type)
            .amount(request.getTransactionAmount())
            .transactionDate(request.getTransactionDate())
            .status(TransactionStatus.REJECTED)
            .note("Validation failed: " + validation.getMessage())
            .auditStatus("N/A")
            .initiatedBy(username)
            .build();
        repository.save(record);
        // Do not send validation rejections to audit/blockchain — no Fineract leg to anchor.

        return TransactionResponse.builder()
            .transactionId(txnId)
            .accountId(accountId)
            .loanId(loanId)
            .transactionType(type.name())
            .amount(request.getTransactionAmount())
            .status("REJECTED")
            .auditStatus("N/A")
            .validation(validation)
            .build();
    }

    private TransactionResponse buildFailedResponse(TransactionRecord record, String message,
                                                     ValidationResult validation) {
        record.setAuditStatus("N/A");
        repository.save(record);
        // Failed core-banking runs must not trigger async audit; that produced misleading AUDIT_FAILED rows.
        return TransactionResponse.builder()
            .transactionId(record.getTransactionId())
            .accountId(record.getAccountId())
            .loanId(record.getLoanId())
            .transactionType(record.getTransactionType().name())
            .amount(record.getAmount())
            .status("FAILED")
            .auditStatus("N/A")
            .validation(validation)
            .build();
    }

    private TransactionResponse mapToResponse(TransactionRecord record) {
        return TransactionResponse.builder()
            .transactionId(record.getTransactionId())
            .accountId(record.getAccountId())
            .loanId(record.getLoanId())
            .transactionType(record.getTransactionType().name())
            .amount(record.getAmount())
            .transactionDate(record.getTransactionDate())
            .status(record.getStatus().name())
            .fineractTransactionId(record.getFineractTransactionId())
            .auditReference(record.getAuditReference())
            .auditStatus(record.getAuditStatus())
            .processedAt(record.getUpdatedAt())
            .build();
    }
}
