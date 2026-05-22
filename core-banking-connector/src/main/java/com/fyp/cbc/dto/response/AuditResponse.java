package com.fyp.cbc.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for audit log data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResponse {
    
    private Long totalFilteredRecords;
    private List<AuditData> pageItems;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditData {
        private Long id;
        private String actionName;
        private String entityName;
        private Long resourceId;
        private String maker;
        private LocalDateTime madeOnDate;
        private String checker;
        private LocalDateTime checkedOnDate;
        private ProcessingResult processingResult;
        private String commandAsJson;
        private Long officeId;
        private String officeName;
        private Long clientId;
        private String clientName;
        private Long loanId;
        private String loanAccountNo;
        private Long savingsAccountId;
        private String savingsAccountNo;
        private String url;
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ProcessingResult {
            private Long id;
            private String code;
            private String value;
        }
    }
    
    /**
     * Search criteria for audit queries.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditSearchCriteria {
        private String actionName;
        private String entityName;
        private Long resourceId;
        private String makerId;
        private String makerDateTimeFrom;
        private String makerDateTimeTo;
        private String checkerId;
        private String checkerDateTimeFrom;
        private String checkerDateTimeTo;
        private String processingResult;
        private Long officeId;
        private Long loanId;
        private Long savingsAccountId;
        private Integer offset;
        private Integer limit;
        private String orderBy;
        private String sortOrder;
    }
}
