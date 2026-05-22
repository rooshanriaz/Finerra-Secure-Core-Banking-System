package com.fyp.fraud.dto;

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
public class RiskScoringResponse {

    private String transactionId;
    private double riskScore;
    private String riskLevel;
    private String recommendation;
    private List<String> riskFactors;
    private String alertId;

    private Double rfProbability;
    private Double isolationScore;

    public enum Recommendation {
        ALLOW, FLAG, BLOCK
    }
}
