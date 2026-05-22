package com.fyp.fraud.dto;

import jakarta.validation.constraints.NotNull;
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
public class RiskScoringRequest {

    @NotNull
    private String transactionId;

    private Long accountId;

    private Long loanId;

    @NotNull
    private String transactionType;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String transactionDate;

    private String initiatedBy;
}
