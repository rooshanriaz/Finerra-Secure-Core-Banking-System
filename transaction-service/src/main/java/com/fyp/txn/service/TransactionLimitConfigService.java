package com.fyp.txn.service;

import com.fyp.txn.dto.TransactionLimitConfigRequest;
import com.fyp.txn.dto.TransactionLimitConfigResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Runtime-configurable transaction thresholds (REQ-4).
 */
@Slf4j
@Getter
@Service
public class TransactionLimitConfigService {

    private BigDecimal maxSingleDeposit;
    private BigDecimal maxSingleWithdrawal;
    private BigDecimal maxDailyDeposit;
    private BigDecimal maxDailyWithdrawal;
    private BigDecimal maxSingleLoanRepayment;
    private int maxDailyTransactions;
    private String updatedBy = "system";
    private String updateReason = "initial configuration";

    public TransactionLimitConfigService(
            @Value("${transaction.limits.max-single-deposit:1000000}") BigDecimal maxSingleDeposit,
            @Value("${transaction.limits.max-single-withdrawal:500000}") BigDecimal maxSingleWithdrawal,
            @Value("${transaction.limits.max-daily-deposit:5000000}") BigDecimal maxDailyDeposit,
            @Value("${transaction.limits.max-daily-withdrawal:2000000}") BigDecimal maxDailyWithdrawal,
            @Value("${transaction.limits.max-single-loan-repayment:2000000}") BigDecimal maxSingleLoanRepayment,
            @Value("${transaction.limits.max-daily-transactions:50}") int maxDailyTransactions) {
        this.maxSingleDeposit = maxSingleDeposit;
        this.maxSingleWithdrawal = maxSingleWithdrawal;
        this.maxDailyDeposit = maxDailyDeposit;
        this.maxDailyWithdrawal = maxDailyWithdrawal;
        this.maxSingleLoanRepayment = maxSingleLoanRepayment;
        this.maxDailyTransactions = maxDailyTransactions;
    }

    public synchronized TransactionLimitConfigResponse getCurrent() {
        return toResponse();
    }

    public synchronized TransactionLimitConfigResponse update(TransactionLimitConfigRequest request, String actor) {
        if (request.getMaxSingleDeposit() != null) this.maxSingleDeposit = request.getMaxSingleDeposit();
        if (request.getMaxSingleWithdrawal() != null) this.maxSingleWithdrawal = request.getMaxSingleWithdrawal();
        if (request.getMaxDailyDeposit() != null) this.maxDailyDeposit = request.getMaxDailyDeposit();
        if (request.getMaxDailyWithdrawal() != null) this.maxDailyWithdrawal = request.getMaxDailyWithdrawal();
        if (request.getMaxSingleLoanRepayment() != null) this.maxSingleLoanRepayment = request.getMaxSingleLoanRepayment();
        if (request.getMaxDailyTransactions() != null) this.maxDailyTransactions = request.getMaxDailyTransactions();

        this.updatedBy = actor;
        this.updateReason = request.getJustification();

        log.warn("Transaction limits updated by {} with reason: {}", actor, request.getJustification());
        return toResponse();
    }

    private TransactionLimitConfigResponse toResponse() {
        return TransactionLimitConfigResponse.builder()
                .maxSingleDeposit(maxSingleDeposit)
                .maxSingleWithdrawal(maxSingleWithdrawal)
                .maxDailyDeposit(maxDailyDeposit)
                .maxDailyWithdrawal(maxDailyWithdrawal)
                .maxSingleLoanRepayment(maxSingleLoanRepayment)
                .maxDailyTransactions(maxDailyTransactions)
                .updatedBy(updatedBy)
                .updateReason(updateReason)
                .build();
    }
}
