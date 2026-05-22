package com.fyp.fraud.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback fraud scoring using deterministic rules and statistical heuristics.
 * Used when the Python ML service is unavailable (circuit breaker open).
 */
@Slf4j
@Service
public class RuleBasedScoringService {

    public Map<String, Object> score(double amount, String transactionType,
                                      int hourOfDay, int dayOfWeek,
                                      double amountToAvgRatio,
                                      int transactionVelocity,
                                      int daysSinceAccountCreation) {

        double riskScore = 0.0;
        List<String> riskFactors = new ArrayList<>();

        // High amount relative to average
        if (amountToAvgRatio > 5.0) {
            riskScore += 0.30;
            riskFactors.add("high_amount_ratio");
        } else if (amountToAvgRatio > 3.0) {
            riskScore += 0.15;
            riskFactors.add("elevated_amount_ratio");
        }

        // Night-time transaction
        if (hourOfDay < 6 || hourOfDay > 22) {
            riskScore += 0.15;
            riskFactors.add("night_transaction");
        }

        // High velocity
        if (transactionVelocity >= 5) {
            riskScore += 0.25;
            riskFactors.add("high_velocity");
        } else if (transactionVelocity >= 3) {
            riskScore += 0.10;
            riskFactors.add("elevated_velocity");
        }

        // Large absolute amount
        if (amount > 500_000) {
            riskScore += 0.20;
            riskFactors.add("large_amount");
        } else if (amount > 200_000) {
            riskScore += 0.10;
            riskFactors.add("significant_amount");
        }

        // New account
        if (daysSinceAccountCreation < 7) {
            riskScore += 0.20;
            riskFactors.add("very_new_account");
        } else if (daysSinceAccountCreation < 30) {
            riskScore += 0.10;
            riskFactors.add("new_account");
        }

        // Withdrawal bias
        if ("WITHDRAWAL".equalsIgnoreCase(transactionType)) {
            riskScore += 0.05;
        }

        riskScore = Math.min(riskScore, 1.0);

        String riskLevel;
        if (riskScore >= 0.85) riskLevel = "CRITICAL";
        else if (riskScore >= 0.60) riskLevel = "HIGH";
        else if (riskScore >= 0.30) riskLevel = "MEDIUM";
        else riskLevel = "LOW";

        Map<String, Object> result = new HashMap<>();
        result.put("risk_score", Math.round(riskScore * 10000.0) / 10000.0);
        result.put("is_fraudulent", riskScore >= 0.60);
        result.put("rf_probability", riskScore);
        result.put("isolation_score", riskScore);
        result.put("risk_level", riskLevel);
        result.put("risk_factors", riskFactors);

        log.info("Rule-based scoring result: score={}, level={}", riskScore, riskLevel);
        return result;
    }
}
