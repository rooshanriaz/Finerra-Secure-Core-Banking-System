package com.fyp.txn.service;

import com.fyp.txn.dto.TransactionResponse.ValidationResult;
import com.fyp.txn.entity.TransactionRecord.TransactionType;
import com.fyp.txn.repository.TransactionRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Transaction validation service.
 * Enforces amount caps, daily limits, and frequency checks.
 */
@Slf4j
@Service
public class TransactionValidationService {

    private final TransactionRecordRepository repository;
    private final TransactionLimitConfigService limitConfigService;

    public TransactionValidationService(TransactionRecordRepository repository,
                                        TransactionLimitConfigService limitConfigService) {
        this.repository = repository;
        this.limitConfigService = limitConfigService;
    }

    /**
     * Validate a deposit transaction.
     */
    public ValidationResult validateDeposit(Long accountId, BigDecimal amount) {
        BigDecimal maxSingleDeposit = limitConfigService.getMaxSingleDeposit();
        BigDecimal maxDailyDeposit = limitConfigService.getMaxDailyDeposit();
        int maxDailyTransactions = limitConfigService.getMaxDailyTransactions();
        // Single transaction cap
        if (amount.compareTo(maxSingleDeposit) > 0) {
            return ValidationResult.builder()
                .valid(false)
                .message("Deposit amount " + amount + " exceeds maximum single deposit limit of " + maxSingleDeposit)
                .build();
        }

        // Daily limit
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal dailyTotal = repository.sumAmountByAccountAndTypeSince(
            accountId, TransactionType.DEPOSIT, startOfDay);
        BigDecimal remaining = maxDailyDeposit.subtract(dailyTotal);

        if (dailyTotal.add(amount).compareTo(maxDailyDeposit) > 0) {
            return ValidationResult.builder()
                .valid(false)
                .message("Deposit would exceed daily deposit limit of " + maxDailyDeposit 
                    + ". Current daily total: " + dailyTotal + ", remaining: " + remaining)
                .dailyTotalBefore(dailyTotal)
                .dailyLimitRemaining(remaining)
                .build();
        }

        // Daily transaction count
        int count = repository.countTransactionsByAccountSince(accountId, startOfDay);
        if (count >= maxDailyTransactions) {
            return ValidationResult.builder()
                .valid(false)
                .message("Daily transaction limit of " + maxDailyTransactions + " reached")
                .dailyTransactionCount(count)
                .build();
        }

        return ValidationResult.builder()
            .valid(true)
            .message("Validation passed")
            .dailyTotalBefore(dailyTotal)
            .dailyLimitRemaining(remaining.subtract(amount))
            .dailyTransactionCount(count + 1)
            .build();
    }

    /**
     * Validate a withdrawal transaction.
     */
    public ValidationResult validateWithdrawal(Long accountId, BigDecimal amount) {
        BigDecimal maxSingleWithdrawal = limitConfigService.getMaxSingleWithdrawal();
        BigDecimal maxDailyWithdrawal = limitConfigService.getMaxDailyWithdrawal();
        int maxDailyTransactions = limitConfigService.getMaxDailyTransactions();
        if (amount.compareTo(maxSingleWithdrawal) > 0) {
            return ValidationResult.builder()
                .valid(false)
                .message("Withdrawal amount " + amount + " exceeds maximum single withdrawal limit of " + maxSingleWithdrawal)
                .build();
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal dailyTotal = repository.sumAmountByAccountAndTypeSince(
            accountId, TransactionType.WITHDRAWAL, startOfDay);
        BigDecimal remaining = maxDailyWithdrawal.subtract(dailyTotal);

        if (dailyTotal.add(amount).compareTo(maxDailyWithdrawal) > 0) {
            return ValidationResult.builder()
                .valid(false)
                .message("Withdrawal would exceed daily withdrawal limit of " + maxDailyWithdrawal
                    + ". Current daily total: " + dailyTotal + ", remaining: " + remaining)
                .dailyTotalBefore(dailyTotal)
                .dailyLimitRemaining(remaining)
                .build();
        }

        int count = repository.countTransactionsByAccountSince(accountId, startOfDay);
        if (count >= maxDailyTransactions) {
            return ValidationResult.builder()
                .valid(false)
                .message("Daily transaction limit of " + maxDailyTransactions + " reached")
                .dailyTransactionCount(count)
                .build();
        }

        return ValidationResult.builder()
            .valid(true)
            .message("Validation passed")
            .dailyTotalBefore(dailyTotal)
            .dailyLimitRemaining(remaining.subtract(amount))
            .dailyTransactionCount(count + 1)
            .build();
    }

    /**
     * Validate a loan repayment.
     */
    public ValidationResult validateLoanRepayment(Long loanId, BigDecimal amount) {
        BigDecimal maxSingleLoanRepayment = limitConfigService.getMaxSingleLoanRepayment();
        if (amount.compareTo(maxSingleLoanRepayment) > 0) {
            return ValidationResult.builder()
                .valid(false)
                .message("Loan repayment amount " + amount + " exceeds maximum single repayment limit of " + maxSingleLoanRepayment)
                .build();
        }

        return ValidationResult.builder()
            .valid(true)
            .message("Validation passed")
            .build();
    }
}
