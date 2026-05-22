package com.fyp.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditRecordResponse {

    private String auditId;
    private String transactionId;
    private Long accountId;
    private Long loanId;
    private String transactionType;
    private BigDecimal amount;
    private String transactionDate;
    private String fineractTransactionId;
    private String initiatedBy;
    private String dataHash;
    private String fabricTxId;
    private String blockchainStatus;
    private boolean integrityVerified;
    private LocalDateTime recordedAt;
}
