package com.fyp.fraud.config;

import com.fyp.fraud.entity.RiskThreshold;
import com.fyp.fraud.repository.RiskThresholdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds default risk thresholds when the database is empty (block/flag for ML scores
 * plus PKR amount, velocity, and geo settings used by rule boosts in {@link com.fyp.fraud.service.RiskScoringService}).
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RiskThresholdInitializer implements CommandLineRunner {

    public static final String BLOCK = "block";
    public static final String FLAG = "flag";
    public static final String HIGH_VALUE_AMOUNT_PKR = "high_value_amount_pkr";
    public static final String VELOCITY_TX_PER_HOUR = "velocity_tx_per_hour";
    public static final String GEO_ANOMALY_SCORE = "geo_anomaly_score";

    private final RiskThresholdRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        log.info("Seeding default risk thresholds");
        repository.save(th(BLOCK, 0.85, "ML risk score at or above this value triggers BLOCK"));
        repository.save(th(FLAG, 0.60, "ML risk score at or above this value triggers FLAG"));
        repository.save(th(HIGH_VALUE_AMOUNT_PKR, 50_000d, "Transaction amount above this PKR adds risk"));
        repository.save(th(VELOCITY_TX_PER_HOUR, 3d, "Hourly transaction count above this adds risk"));
        repository.save(th(GEO_ANOMALY_SCORE, 80d, "Reserved for geo-based rules when location data is available"));
    }

    private static RiskThreshold th(String name, double value, String description) {
        return RiskThreshold.builder()
            .thresholdName(name)
            .value(value)
            .description(description)
            .updatedBy("system")
            .build();
    }
}
