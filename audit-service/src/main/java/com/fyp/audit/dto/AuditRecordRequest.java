package com.fyp.audit.dto;

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
public class AuditRecordRequest {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    private Long accountId;

    private Long loanId;

    @NotBlank(message = "Transaction type is required")
    private String transactionType;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    private String transactionDate;

    private String fineractTransactionId;

    private String initiatedBy;

    private String sourceIp;
}
