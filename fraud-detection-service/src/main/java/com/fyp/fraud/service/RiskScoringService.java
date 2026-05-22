package com.fyp.fraud.service;

import com.fyp.fraud.config.FraudProperties;
import com.fyp.fraud.dto.RiskScoringRequest;
import com.fyp.fraud.dto.RiskScoringResponse;
import com.fyp.fraud.entity.FraudAlert;
import com.fyp.fraud.entity.FraudAlert.RiskLevel;
import com.fyp.fraud.entity.TransactionProfile;
import com.fyp.fraud.repository.TransactionProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates fraud risk scoring:
 *   1. Enriches the request with account profile context
 *   2. Calls the ML service (or rule-based fallback)
 *   3. Interprets the result against configurable thresholds
 *   4. Creates fraud alerts for flagged/blocked transactions
 *   5. Updates account profiles
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoringService {

    private final MlClientService mlClientService;
    private final FraudAlertService fraudAlertService;
    private final TransactionProfileRepository profileRepository;
    private final FraudProperties fraudProperties;

    public RiskScoringResponse scoreTransaction(RiskScoringRequest request) {
        log.info("Scoring transaction: txnId={}, accountId={}, amount={}, type={}",
            request.getTransactionId(), request.getAccountId(),
            request.getAmount(), request.getTransactionType());

        Long accountId = request.getAccountId() != null ? request.getAccountId() : 0L;
        boolean isNewAccount = false;

        TransactionProfile profile = profileRepository.findByAccountId(accountId).orElse(null);

        if (profile == null) {
            isNewAccount = true;
            profile = TransactionProfile.builder()
                .accountId(accountId)
                .avgAmount(BigDecimal.valueOf(10000))
                .totalTransactions(0L)
                .accountCreatedAt(LocalDateTime.now())
                .build();
        }

        double amountToAvgRatio;
        if (isNewAccount) {
            amountToAvgRatio = request.getAmount()
                .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP)
                .doubleValue();
        } else if (profile.getAvgAmount() != null && profile.getAvgAmount().compareTo(BigDecimal.ZERO) > 0) {
            amountToAvgRatio = request.getAmount()
                .divide(profile.getAvgAmount(), 4, RoundingMode.HALF_UP)
                .doubleValue();
        } else {
            amountToAvgRatio = request.getAmount()
                .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP)
                .doubleValue();
        }

        int velocity = 0;
        if (profile.getLastTransactionAt() != null
                && profile.getLastTransactionAt().isAfter(LocalDateTime.now().minusHours(1))) {
            velocity = Math.min(profile.getTotalTransactions().intValue(), 10);
        }

        int daysSinceCreation;
        if (isNewAccount) {
            daysSinceCreation = 0;
        } else if (profile.getAccountCreatedAt() != null) {
            daysSinceCreation = (int) java.time.temporal.ChronoUnit.DAYS.between(
                profile.getAccountCreatedAt().toLocalDate(), java.time.LocalDate.now());
        } else {
            daysSinceCreation = 0;
        }

        LocalDateTime now = LocalDateTime.now();
        int hourOfDay = now.getHour();
        int dayOfWeek = now.getDayOfWeek().getValue() % 7;

        // Call ML service
        Map<String, Object> mlResult = mlClientService.score(
            request.getAmount().doubleValue(),
            request.getTransactionType(),
            hourOfDay,
            dayOfWeek,
            amountToAvgRatio,
            velocity,
            daysSinceCreation
        );

        double riskScore = toDouble(mlResult.get("risk_score"));
        String riskLevel = (String) mlResult.getOrDefault("risk_level", "LOW");
        List<String> riskFactors = new ArrayList<>(safeRiskFactors(mlResult.get("risk_factors")));
        Double rfProbability = toDoubleOrNull(mlResult.get("rf_probability"));
        Double isolationScore = toDoubleOrNull(mlResult.get("isolation_score"));

        // Admin-configured PKR / velocity boosts (persisted thresholds)
        riskScore = applyRuleBoosts(request, velocity, riskScore, riskFactors);

        // Determine recommendation against thresholds (after boosts)
        double blockThreshold = getThreshold("block", fraudProperties.getThresholds().getBlock());
        double flagThreshold = getThreshold("flag", fraudProperties.getThresholds().getFlag());

        String recommendation;
        if (riskScore >= blockThreshold) {
            recommendation = "BLOCK";
        } else if (riskScore >= flagThreshold) {
            recommendation = "FLAG";
        } else {
            recommendation = "ALLOW";
        }

        // Create fraud alert for flagged/blocked transactions
        String alertId = null;
        if (!"ALLOW".equals(recommendation)) {
            FraudAlert alert = fraudAlertService.createAlert(
                request.getTransactionId(),
                accountId,
                BigDecimal.valueOf(riskScore),
                parseRiskLevel(riskLevel),
                riskFactors,
                recommendation,
                request.getAmount(),
                request.getTransactionType(),
                request.getInitiatedBy()
            );
            alertId = alert.getAlertId();
            log.warn("Fraud alert created: alertId={}, txnId={}, score={}, recommendation={}",
                alertId, request.getTransactionId(), riskScore, recommendation);
        }

        // Update account profile
        profile.updateWithTransaction(request.getAmount());
        profileRepository.save(profile);

        log.info("Risk scoring complete: txnId={}, score={}, level={}, recommendation={}",
            request.getTransactionId(), riskScore, riskLevel, recommendation);

        return RiskScoringResponse.builder()
            .transactionId(request.getTransactionId())
            .riskScore(riskScore)
            .riskLevel(riskLevel)
            .recommendation(recommendation)
            .riskFactors(riskFactors)
            .alertId(alertId)
            .rfProbability(rfProbability)
            .isolationScore(isolationScore)
            .build();
    }

    private RiskLevel parseRiskLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return RiskLevel.MEDIUM;
        }
        String normalized = raw.trim().toUpperCase().replace(' ', '_');
        try {
            return RiskLevel.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown risk level from scorer '{}', using MEDIUM", raw);
            return RiskLevel.MEDIUM;
        }
    }

    private List<String> safeRiskFactors(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }

    private double getThreshold(String name, double defaultValue) {
        return fraudAlertService.getThresholdValue(name).orElse(defaultValue);
    }

    /**
     * Raises risk score when amount or velocity exceed configured thresholds (PKR / tx per hour).
     */
    private double applyRuleBoosts(RiskScoringRequest request, int velocity, double riskScore,
                                   List<String> riskFactors) {
        double amt = request.getAmount().doubleValue();
        double highValue = fraudAlertService.getThresholdValue("high_value_amount_pkr").orElse(50_000.0);
        if (amt > highValue) {
            double boosted = Math.min(1.0, riskScore + 0.12);
            if (boosted > riskScore) {
                riskFactors.add("high_value_amount_pkr");
            }
            riskScore = boosted;
        }
        double velTh = fraudAlertService.getThresholdValue("velocity_tx_per_hour").orElse(3.0);
        if (velocity > velTh) {
            double boosted = Math.min(1.0, riskScore + 0.08);
            if (boosted > riskScore) {
                riskFactors.add("velocity_above_threshold");
            }
            riskScore = boosted;
        }
        return riskScore;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private Double toDoubleOrNull(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return null;
    }
}
