package com.fyp.txn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionApprovalRequestPayload {

    private Long accountId;
    private Long loanId;

    @NotBlank(message = "transactionType is required")
    private String transactionType; // DEPOSIT, WITHDRAWAL, LOAN_REPAYMENT

    @NotNull(message = "transactionAmount is required")
    @DecimalMin(value = "0.01", message = "transactionAmount must be greater than zero")
    private BigDecimal transactionAmount;

    @NotBlank(message = "transactionDate is required")
    private String transactionDate;

    private String note;
}

