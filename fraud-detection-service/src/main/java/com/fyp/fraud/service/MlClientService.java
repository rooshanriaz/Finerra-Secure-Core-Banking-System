package com.fyp.fraud.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * WebClient wrapper for the Python ML fraud scoring service.
 * Falls back to rule-based scoring when the ML service is unavailable.
 */
@Slf4j
@Service
public class MlClientService {

    private final WebClient webClient;
    private final RuleBasedScoringService fallbackService;
    private final int timeoutMs;

    public MlClientService(
            @Qualifier("mlWebClient") WebClient mlWebClient,
            @Value("${ml-service.timeout-ms:5000}") int timeoutMs,
            RuleBasedScoringService fallbackService) {
        this.webClient = mlWebClient;
        this.fallbackService = fallbackService;
        this.timeoutMs = timeoutMs;
        log.info("ML client using configured WebClient (timeouts: response={}ms)", timeoutMs);
    }

    @CircuitBreaker(name = "mlService", fallbackMethod = "mlFallback")
    public Map<String, Object> score(double amount, String transactionType,
                                      int hourOfDay, int dayOfWeek,
                                      double amountToAvgRatio,
                                      int transactionVelocity,
                                      int daysSinceAccountCreation) {

        Map<String, Object> requestBody = Map.of(
            "amount", amount,
            "transaction_type", transactionType,
            "hour_of_day", hourOfDay,
            "day_of_week", dayOfWeek,
            "amount_to_avg_ratio", amountToAvgRatio,
            "transaction_velocity", transactionVelocity,
            "days_since_account_creation", daysSinceAccountCreation
        );

        log.debug("Calling ML /predict (type={}, hour={}, dow={})",
            transactionType, hourOfDay, dayOfWeek);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.post()
            .uri("/predict")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofMillis(timeoutMs))
            .block();

        if (response == null || response.isEmpty()) {
            log.warn("ML service returned empty body; using rule-based fallback");
            return fallbackService.score(amount, transactionType, hourOfDay, dayOfWeek,
                amountToAvgRatio, transactionVelocity, daysSinceAccountCreation);
        }

        log.debug("ML service scored: risk_score={}", response.get("risk_score"));
        return response;
    }

    @SuppressWarnings("unused")
    public Map<String, Object> mlFallback(double amount, String transactionType,
                                            int hourOfDay, int dayOfWeek,
                                            double amountToAvgRatio,
                                            int transactionVelocity,
                                            int daysSinceAccountCreation,
                                            Throwable t) {
        log.warn("ML service unavailable ({}), using rule-based fallback", t.getMessage());
        return fallbackService.score(amount, transactionType, hourOfDay, dayOfWeek,
            amountToAvgRatio, transactionVelocity, daysSinceAccountCreation);
    }
}
