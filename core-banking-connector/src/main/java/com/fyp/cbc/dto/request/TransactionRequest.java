package com.fyp.cbc.dto.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for account transactions (deposit, withdrawal, repayment).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionRequest {
    
    @NotNull(message = "Transaction amount is required")
    @Positive(message = "Transaction amount must be positive")
    private BigDecimal transactionAmount;
    
    @NotBlank(message = "Transaction date is required")
    private String transactionDate;
    
    private Long paymentTypeId;
    
    private String accountNumber;
    
    private String checkNumber;
    
    private String routingCode;
    
    private String receiptNumber;
    
    private String bankNumber;
    
    private String note;
    
    @Builder.Default
    private String locale = "en";
    
    @Builder.Default
    private String dateFormat = "dd MMMM yyyy";
}
