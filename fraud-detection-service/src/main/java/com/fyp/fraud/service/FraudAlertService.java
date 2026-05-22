package com.fyp.fraud.service;

import com.fyp.fraud.dto.FraudAlertResponse;
import com.fyp.fraud.entity.FraudAlert;
import com.fyp.fraud.entity.FraudAlert.AlertStatus;
import com.fyp.fraud.entity.FraudAlert.RiskLevel;
import com.fyp.fraud.entity.RiskThreshold;
import com.fyp.fraud.repository.FraudAlertRepository;
import com.fyp.fraud.repository.RiskThresholdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudAlertService {

    private final FraudAlertRepository alertRepository;
    private final RiskThresholdRepository thresholdRepository;

    public FraudAlert createAlert(String transactionId, Long accountId,
                                   BigDecimal riskScore, RiskLevel riskLevel,
                                   List<String> riskFactors, String recommendation,
                                   BigDecimal transactionAmount, String transactionType,
                                   String initiatedBy) {

        String alertId = "FRD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        FraudAlert alert = FraudAlert.builder()
            .alertId(alertId)
            .transactionId(transactionId)
            .accountId(accountId)
            .riskScore(riskScore)
            .riskLevel(riskLevel)
            .riskFactors(String.join(",", riskFactors))
            .status(AlertStatus.OPEN)
            .recommendation(recommendation)
            .transactionAmount(transactionAmount)
            .transactionType(transactionType)
            .initiatedBy(initiatedBy)
            .build();

        return alertRepository.save(alert);
    }

    public Optional<FraudAlertResponse> getAlert(String alertId) {
        return alertRepository.findByAlertId(alertId).map(this::toResponse);
    }

    public List<FraudAlertResponse> getRecentAlerts() {
        return alertRepository.findTop50ByOrderByCreatedAtDesc()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<FraudAlertResponse> getAlertsByStatus(AlertStatus status) {
        return alertRepository.findByStatusOrderByCreatedAtDesc(status)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<FraudAlertResponse> getAlertsByAccount(Long accountId) {
        return alertRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Optional<FraudAlertResponse> updateAlertStatus(String alertId, AlertStatus newStatus,
                                                           String resolvedBy, String resolutionNote) {
        return alertRepository.findByAlertId(alertId).map(alert -> {
            alert.setStatus(newStatus);
            if (newStatus == AlertStatus.RESOLVED || newStatus == AlertStatus.FALSE_POSITIVE) {
                alert.setResolvedBy(resolvedBy);
                alert.setResolvedAt(LocalDateTime.now());
                alert.setResolutionNote(resolutionNote);
            }
            alertRepository.save(alert);
            log.info("Alert {} updated to status {} by {}", alertId, newStatus, resolvedBy);
            return toResponse(alert);
        });
    }

    public Map<String, Object> getAlertStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", alertRepository.count());
        stats.put("open", alertRepository.countByStatus(AlertStatus.OPEN));
        stats.put("investigating", alertRepository.countByStatus(AlertStatus.INVESTIGATING));
        stats.put("resolved", alertRepository.countByStatus(AlertStatus.RESOLVED));
        stats.put("false_positive", alertRepository.countByStatus(AlertStatus.FALSE_POSITIVE));
        stats.put("critical", alertRepository.countByRiskLevel(RiskLevel.CRITICAL));
        stats.put("high", alertRepository.countByRiskLevel(RiskLevel.HIGH));
        return stats;
    }

    // --- Threshold management ---

    public Optional<Double> getThresholdValue(String name) {
        return thresholdRepository.findByThresholdName(name).map(RiskThreshold::getValue);
    }

    public List<RiskThreshold> getAllThresholds() {
        return thresholdRepository.findAll();
    }

    public RiskThreshold upsertThreshold(String name, double value, String description, String updatedBy) {
        RiskThreshold threshold = thresholdRepository.findByThresholdName(name)
            .orElse(RiskThreshold.builder().thresholdName(name).build());
        threshold.setValue(value);
        threshold.setDescription(description);
        threshold.setUpdatedBy(updatedBy);
        return thresholdRepository.save(threshold);
    }

    // --- Mapping ---

    private FraudAlertResponse toResponse(FraudAlert alert) {
        List<String> factors = alert.getRiskFactors() != null && !alert.getRiskFactors().isBlank()
            ? Arrays.asList(alert.getRiskFactors().split(","))
            : List.of();

        return FraudAlertResponse.builder()
            .alertId(alert.getAlertId())
            .transactionId(alert.getTransactionId())
            .accountId(alert.getAccountId())
            .riskScore(alert.getRiskScore())
            .riskLevel(alert.getRiskLevel().name())
            .riskFactors(factors)
            .status(alert.getStatus().name())
            .recommendation(alert.getRecommendation())
            .transactionAmount(alert.getTransactionAmount())
            .transactionType(alert.getTransactionType())
            .initiatedBy(alert.getInitiatedBy())
            .resolvedBy(alert.getResolvedBy())
            .resolutionNote(alert.getResolutionNote())
            .resolvedAt(alert.getResolvedAt())
            .createdAt(alert.getCreatedAt())
            .build();
    }
}
