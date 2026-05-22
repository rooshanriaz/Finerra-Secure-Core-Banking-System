package com.fyp.kyc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AmlScreeningResponse {

    private String result;     // CLEAR, MATCH_FOUND, POTENTIAL_MATCH
    private String riskLevel;  // LOW, MEDIUM, HIGH, CRITICAL
    private List<MatchDetail> matches;
    private String details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchDetail {
        private String matchedName;
        private String listName;
        private Double matchScore;
        private String entityType;
        private String reason;
    }
}
