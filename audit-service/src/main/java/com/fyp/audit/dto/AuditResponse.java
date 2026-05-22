package com.fyp.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditResponse {

    private String auditId;
    private String transactionId;
    private Long accountId;
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private String transactionDate;
    private String auditHash;
    private String transactionHash;
    private String previousHash;
    private String fabricTxId;
    private boolean blockchainAnchored;
    private String sourceService;
    private String initiatedBy;
    private String status;
    private String createdAt;
}
