package com.fyp.cbc.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for loan rescheduling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleRequest {
    
    @NotNull(message = "Loan ID is required")
    private Long loanId;
    
    @NotBlank(message = "Reschedule from date is required")
    private String rescheduleFromDate;
    
    @NotNull(message = "Reschedule reason ID is required")
    private Long rescheduleReasonId;
    
    private String submittedOnDate;
    
    private String rescheduleReasonComment;
    
    private String adjustedDueDate;
    
    private Integer graceOnPrincipal;
    
    private Integer graceOnInterest;
    
    private Integer extraTerms;
    
    private BigDecimal newInterestRate;
    
    @Builder.Default
    private String locale = "en";
    
    @Builder.Default
    private String dateFormat = "dd MMMM yyyy";
}
