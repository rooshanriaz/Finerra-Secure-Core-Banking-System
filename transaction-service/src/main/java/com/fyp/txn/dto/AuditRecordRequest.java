package com.fyp.txn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for sending audit record requests to audit-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecordRequest {
    private String transactionId;
    private Long accountId;
    private Long loanId;
    private String transactionType;
    private BigDecimal amount;
    private String transactionDate;
    private String fineractTransactionId;
    private String initiatedBy;
    private String sourceIp;
}
