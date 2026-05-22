package com.fyp.cbc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for transaction data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    
    private Long officeId;
    private Long clientId;
    private Long savingsId;
    private Long resourceId;
    private ChangesData changes;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangesData {
        private String locale;
        private String dateFormat;
        private String transactionDate;
        private BigDecimal transactionAmount;
        private Long paymentTypeId;
    }
    
    /**
     * Detailed transaction data for retrieving transaction history.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDetailData {
        private Long id;
        private TransactionType transactionType;
        private Long accountId;
        private String accountNo;
        private LocalDate date;
        private CurrencyData currency;
        private PaymentDetailData paymentDetailData;
        private BigDecimal amount;
        private BigDecimal runningBalance;
        private Boolean reversed;
        private String submittedByUsername;
        private String note;
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TransactionType {
            private Long id;
            private String code;
            private String value;
            private Boolean deposit;
            private Boolean withdrawal;
            private Boolean interestPosting;
            private Boolean feeDeduction;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CurrencyData {
            private String code;
            private String name;
            private Integer decimalPlaces;
            private String displaySymbol;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PaymentDetailData {
            private Long id;
            private PaymentType paymentType;
            private String accountNumber;
            private String checkNumber;
            private String routingCode;
            private String receiptNumber;
            private String bankNumber;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PaymentType {
            private Long id;
            private String name;
        }
    }
    
    /**
     * Response for journal entries listing.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalEntryResponse {
        private Long totalFilteredRecords;
        private List<JournalEntryData> pageItems;
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class JournalEntryData {
            private Long id;
            private Long officeId;
            private String officeName;
            private String glAccountName;
            private Long glAccountId;
            private String glAccountCode;
            private GlAccountType glAccountType;
            private LocalDate transactionDate;
            private EntryType entryType;
            private BigDecimal amount;
            private String transactionId;
            private Boolean manualEntry;
            private String createdByUserName;
            private LocalDate createdDate;
            private String comments;
            private Boolean reversed;
            private String referenceNumber;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class GlAccountType {
            private Long id;
            private String code;
            private String value;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class EntryType {
            private Long id;
            private String code;
            private String value;
        }
    }
}
