package com.fyp.txn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLimitConfigRequest {

    @Positive(message = "maxSingleDeposit must be positive")
    private BigDecimal maxSingleDeposit;

    @Positive(message = "maxSingleWithdrawal must be positive")
    private BigDecimal maxSingleWithdrawal;

    @Positive(message = "maxDailyDeposit must be positive")
    private BigDecimal maxDailyDeposit;

    @Positive(message = "maxDailyWithdrawal must be positive")
    private BigDecimal maxDailyWithdrawal;

    @Positive(message = "maxSingleLoanRepayment must be positive")
    private BigDecimal maxSingleLoanRepayment;

    @Positive(message = "maxDailyTransactions must be positive")
    private Integer maxDailyTransactions;

    @NotBlank(message = "justification is required")
    private String justification;
}
