package com.fyp.fraud.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FraudAlertResponse {

    private String alertId;
    private String transactionId;
    private Long accountId;
    private BigDecimal riskScore;
    private String riskLevel;
    private List<String> riskFactors;
    private String status;
    private String recommendation;
    private BigDecimal transactionAmount;
    private String transactionType;
    private String initiatedBy;
    private String resolvedBy;
    private String resolutionNote;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
