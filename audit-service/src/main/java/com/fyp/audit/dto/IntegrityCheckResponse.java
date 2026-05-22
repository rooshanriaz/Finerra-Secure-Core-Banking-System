package com.fyp.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntegrityCheckResponse {

    private int totalRecords;
    private int verifiedCount;
    private int failedCount;
    private boolean allIntact;
    private List<FailedRecord> failedRecords;
    private LocalDateTime checkedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedRecord {
        private String auditId;
        private String transactionId;
        private String expectedHash;
        private String actualHash;
        private String reason;
    }
}
