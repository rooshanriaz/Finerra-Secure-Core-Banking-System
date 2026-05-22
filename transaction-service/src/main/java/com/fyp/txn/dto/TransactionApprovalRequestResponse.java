package com.fyp.txn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionApprovalRequestResponse {
    private String requestId;
    private Long accountId;
    private Long loanId;
    private String transactionType;
    private BigDecimal transactionAmount;
    private String transactionDate;
    private String note;
    private String requestedBy;
    private String status;
    private String decidedBy;
    private String decisionNote;
    private LocalDateTime decidedAt;
    private String executedTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

