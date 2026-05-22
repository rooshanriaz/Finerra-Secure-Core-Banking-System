package com.fyp.cbc.dto.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new loan application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateLoanRequest {
    
    @NotNull(message = "Client ID is required")
    private Long clientId;
    
    @NotNull(message = "Product ID is required")
    private Long productId;
    
    @NotNull(message = "Principal amount is required")
    @Positive(message = "Principal must be positive")
    private BigDecimal principal;
    
    @NotNull(message = "Loan term frequency is required")
    private Integer loanTermFrequency;
    
    @NotNull(message = "Loan term frequency type is required")
    private Integer loanTermFrequencyType;
    
    private Integer numberOfRepayments;
    
    private Integer repaymentEvery;
    
    private Integer repaymentFrequencyType;
    
    private BigDecimal interestRatePerPeriod;
    
    private Integer interestType;
    
    private Integer interestCalculationPeriodType;
    
    private Integer amortizationType;
    
    private String expectedDisbursementDate;
    
    private String submittedOnDate;
    
    private Integer transactionProcessingStrategyId;
    
    private String transactionProcessingStrategyCode;
    
    private String loanType;
    
    private Long loanOfficerId;
    
    private Long fundId;
    
    private String externalId;
    
    @Builder.Default
    private String locale = "en";
    
    @Builder.Default
    private String dateFormat = "dd MMMM yyyy";
}
