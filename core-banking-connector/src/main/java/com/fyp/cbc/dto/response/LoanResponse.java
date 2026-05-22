package com.fyp.cbc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fyp.cbc.config.FineractLocalDateDeserializer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for loan data from Apache Fineract.
 *
 * Fineract returns dates as integer arrays [year, month, day].  The custom
 * {@link FineractLocalDateDeserializer} handles both array and string forms.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoanResponse {

    private Long id;
    private Long loanId;
    private Long resourceId;
    private Long officeId;
    private String accountNo;
    private String externalId;
    private StatusData status;
    private Long clientId;
    private String clientName;
    private String clientAccountNo;
    private Long clientOfficeId;
    private Long loanProductId;
    private String loanProductName;
    private String loanProductDescription;
    private Long loanOfficerId;
    private String loanOfficerName;
    private LoanType loanType;
    private CurrencyData currency;
    private BigDecimal principal;
    private BigDecimal approvedPrincipal;
    private BigDecimal proposedPrincipal;
    private Integer termFrequency;
    private TermPeriodFrequencyType termPeriodFrequencyType;
    private Integer numberOfRepayments;
    private Integer repaymentEvery;
    private RepaymentFrequencyType repaymentFrequencyType;
    private BigDecimal interestRatePerPeriod;
    private BigDecimal annualInterestRate;
    private InterestRateFrequencyType interestRateFrequencyType;
    private InterestType interestType;
    private InterestCalculationPeriodType interestCalculationPeriodType;
    private AmortizationType amortizationType;

    @JsonDeserialize(using = FineractLocalDateDeserializer.class)
    private LocalDate expectedDisbursementDate;

    @JsonDeserialize(using = FineractLocalDateDeserializer.class)
    private LocalDate actualDisbursementDate;

    @JsonDeserialize(using = FineractLocalDateDeserializer.class)
    private LocalDate expectedMaturityDate;

    private TimelineData timeline;
    private SummaryData summary;
    private List<RepaymentScheduleData> repaymentSchedule;

    // CBC two-step review workflow metadata (not native Fineract fields).
    private String workflowStatus;
    private Boolean forwardedToManager;
    private String forwardedBy;
    private String managerDecisionBy;
    private String workflowNote;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusData {
        private Long id;
        private String code;
        private String value;
        private Boolean pendingApproval;
        private Boolean waitingForDisbursal;
        private Boolean active;
        private Boolean closedObligationsMet;
        private Boolean closedWrittenOff;
        private Boolean closedRescheduled;
        private Boolean closed;
        private Boolean overpaid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoanType {
        private Long id;
        private String code;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrencyData {
        private String code;
        private String name;
        private Integer decimalPlaces;
        private String displaySymbol;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TermPeriodFrequencyType {
        private Long id;
        private String code;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepaymentFrequencyType {
        private Long id;
        private String code;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InterestRateFrequencyType {
        private Long id;
        private String code;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InterestType {
        private Long id;
        private String code;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InterestCalculationPeriodType {
        private Long id;
        private String code;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AmortizationType {
        private Long id;
        private String code;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimelineData {
        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate submittedOnDate;
        private String submittedByUsername;

        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate approvedOnDate;
        private String approvedByUsername;

        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate expectedDisbursementDate;

        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate actualDisbursementDate;
        private String disbursedByUsername;

        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate expectedMaturityDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SummaryData {
        private CurrencyData currency;
        private BigDecimal principalDisbursed;
        private BigDecimal principalPaid;
        private BigDecimal principalWrittenOff;
        private BigDecimal principalOutstanding;
        private BigDecimal principalOverdue;
        private BigDecimal interestCharged;
        private BigDecimal interestPaid;
        private BigDecimal interestWaived;
        private BigDecimal interestWrittenOff;
        private BigDecimal interestOutstanding;
        private BigDecimal interestOverdue;
        private BigDecimal feeChargesCharged;
        private BigDecimal feeChargesPaid;
        private BigDecimal feeChargesWaived;
        private BigDecimal feeChargesWrittenOff;
        private BigDecimal feeChargesOutstanding;
        private BigDecimal feeChargesOverdue;
        private BigDecimal penaltyChargesCharged;
        private BigDecimal penaltyChargesPaid;
        private BigDecimal penaltyChargesWaived;
        private BigDecimal penaltyChargesWrittenOff;
        private BigDecimal penaltyChargesOutstanding;
        private BigDecimal penaltyChargesOverdue;
        private BigDecimal totalExpectedRepayment;
        private BigDecimal totalRepayment;
        private BigDecimal totalExpectedCostOfLoan;
        private BigDecimal totalCostOfLoan;
        private BigDecimal totalWaived;
        private BigDecimal totalWrittenOff;
        private BigDecimal totalOutstanding;
        private BigDecimal totalOverdue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepaymentScheduleData {
        private Integer period;

        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate fromDate;

        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate dueDate;

        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate obligationsMetOnDate;

        private Boolean complete;
        private BigDecimal principalDue;
        private BigDecimal principalPaid;
        private BigDecimal principalOutstanding;
        private BigDecimal interestDue;
        private BigDecimal interestPaid;
        private BigDecimal interestOutstanding;
        private BigDecimal feeChargesDue;
        private BigDecimal feeChargesPaid;
        private BigDecimal feeChargesOutstanding;
        private BigDecimal penaltyChargesDue;
        private BigDecimal penaltyChargesPaid;
        private BigDecimal penaltyChargesOutstanding;
        private BigDecimal totalDueForPeriod;
        private BigDecimal totalPaidForPeriod;
        private BigDecimal totalOutstandingForPeriod;
    }
}
