package com.fyp.cbc.dto.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new savings account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateSavingsAccountRequest {
    
    @NotNull(message = "Client ID is required")
    private Long clientId;
    
    @NotNull(message = "Product ID is required")
    private Long productId;
    
    private String externalId;
    
    private String submittedOnDate;
    
    private BigDecimal nominalAnnualInterestRate;
    
    private Integer interestCompoundingPeriodType;
    
    private Integer interestPostingPeriodType;
    
    private Integer interestCalculationType;
    
    private Integer interestCalculationDaysInYearType;
    
    private BigDecimal minRequiredOpeningBalance;
    
    private Integer lockinPeriodFrequency;
    
    private Integer lockinPeriodFrequencyType;
    
    private Boolean withdrawalFeeForTransfers;
    
    private Boolean allowOverdraft;
    
    private BigDecimal overdraftLimit;
    
    @Builder.Default
    private String locale = "en";
    
    @Builder.Default
    private String dateFormat = "dd MMMM yyyy";
}
