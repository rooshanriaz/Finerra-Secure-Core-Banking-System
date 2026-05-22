package com.fyp.txn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    private String transactionId;
    private Long accountId;
    private Long loanId;
    private String transactionType;
    private BigDecimal amount;
    private String transactionDate;
    private String status;
    private String auditReference;
    private String auditStatus;
    private String fineractTransactionId;
    private LocalDateTime processedAt;
    private ValidationResult validation;

    private Double riskScore;
    private String riskLevel;
    private List<String> riskFactors;
    private String fraudAlertId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private String message;
        private BigDecimal dailyTotalBefore;
        private BigDecimal dailyLimitRemaining;
        private int dailyTransactionCount;
    }
}
