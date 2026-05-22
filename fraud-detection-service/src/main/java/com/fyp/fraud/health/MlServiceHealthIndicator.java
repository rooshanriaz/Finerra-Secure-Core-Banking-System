package com.fyp.fraud.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Reports readiness of the Python ML fraud inference service.
 */
@Component("mlFraudInference")
public class MlServiceHealthIndicator implements HealthIndicator {

    private final WebClient mlWebClient;

    public MlServiceHealthIndicator(@Qualifier("mlWebClient") WebClient mlWebClient) {
        this.mlWebClient = mlWebClient;
    }

    @Override
    public Health health() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mlWebClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .block();

            if (body == null) {
                return Health.up()
                    .withDetail("mlInference", "UNKNOWN")
                    .withDetail("note", "Empty ML /health response; rule-based scoring applies")
                    .build();
            }

            Object loaded = body.get("model_loaded");
            Object status = body.get("status");
            boolean inferenceReady = Boolean.TRUE.equals(loaded) && "UP".equals(String.valueOf(status));

            Health.Builder b = Health.up()
                .withDetail("modelLoaded", loaded)
                .withDetail("mlStatus", status)
                .withDetail("modelVersion", body.get("model_version"));
            if (!inferenceReady) {
                b.withDetail("mlInference", "DEGRADED")
                    .withDetail("note", "ML models not ready; rule-based scoring applies");
            } else {
                b.withDetail("mlInference", "READY");
            }
            return b.build();
        } catch (Exception e) {
            return Health.up()
                .withDetail("mlInference", "UNREACHABLE")
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("note", "ML service unreachable; circuit breaker will use rule-based scoring")
                .build();
        }
    }
}
