package com.fyp.cbc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for savings account data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsAccountResponse {
    
    private Long id;
    private Long savingsId;
    private Long resourceId;
    private Long officeId;
    private String accountNo;
    private String externalId;
    private Long clientId;
    private String clientName;
    private Long savingsProductId;
    private String savingsProductName;
    private Long fieldOfficerId;
    private StatusData status;
    private TimelineData timeline;
    private CurrencyData currency;
    private BigDecimal nominalAnnualInterestRate;
    private InterestCompoundingPeriodType interestCompoundingPeriodType;
    private InterestPostingPeriodType interestPostingPeriodType;
    private InterestCalculationType interestCalculationType;
    private InterestCalculationDaysInYearType interestCalculationDaysInYearType;
    private BigDecimal minRequiredOpeningBalance;
    private Integer lockinPeriodFrequency;
    private LockinPeriodFrequencyType lockinPeriodFrequencyType;
    private Boolean withdrawalFeeForTransfers;
    private Boolean allowOverdraft;
    private BigDecimal overdraftLimit;
    private SummaryData summary;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusData {
        private Long id;
        private String code;
        private String value;
        private Boolean submittedAndPendingApproval;
        private Boolean approved;
        private Boolean rejected;
        private Boolean withdrawnByApplicant;
        private Boolean active;
        private Boolean closed;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineData {
        private LocalDate submittedOnDate;
        private String submittedByUsername;
        private LocalDate approvedOnDate;
        private String approvedByUsername;
        private LocalDate activatedOnDate;
        private String activatedByUsername;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyData {
        private String code;
        private String name;
        private Integer decimalPlaces;
        private String displaySymbol;
        private String nameCode;
        private String displayLabel;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestCompoundingPeriodType {
        private Long id;
        private String code;
        private String value;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestPostingPeriodType {
        private Long id;
        private String code;
        private String value;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestCalculationType {
        private Long id;
        private String code;
        private String value;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestCalculationDaysInYearType {
        private Long id;
        private String code;
        private String value;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LockinPeriodFrequencyType {
        private Long id;
        private String code;
        private String value;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryData {
        private CurrencyData currency;
        private BigDecimal totalDeposits;
        private BigDecimal totalWithdrawals;
        private BigDecimal totalInterestEarned;
        private BigDecimal totalInterestPosted;
        private BigDecimal accountBalance;
        private BigDecimal availableBalance;
    }
}
