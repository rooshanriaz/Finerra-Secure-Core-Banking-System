package com.fyp.txn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLimitConfigResponse {
    private BigDecimal maxSingleDeposit;
    private BigDecimal maxSingleWithdrawal;
    private BigDecimal maxDailyDeposit;
    private BigDecimal maxDailyWithdrawal;
    private BigDecimal maxSingleLoanRepayment;
    private Integer maxDailyTransactions;
    private String updatedBy;
    private String updateReason;
}
