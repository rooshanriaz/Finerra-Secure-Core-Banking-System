package com.fyp.txn.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls the fraud-detection-service to score transactions before execution.
 * Falls back to allowing the transaction if the fraud service is unavailable.
 */
@Slf4j
@Service
public class FraudScoringClient {

    private final WebClient webClient;
    private final MachineTokenService machineTokenService;

    public FraudScoringClient(
            @Value("${services.fraud-detection-service.url:http://localhost:8087/api}") String fraudServiceUrl,
            MachineTokenService machineTokenService) {
        this.machineTokenService = machineTokenService;

        this.webClient = WebClient.builder()
            .baseUrl(fraudServiceUrl)
            .filter((request, next) -> {
                String token = resolveBearerToken();
                var authorized = org.springframework.web.reactive.function.client.ClientRequest.from(request)
                        .headers(headers -> headers.setBearerAuth(token))
                        .build();
                return next.exchange(authorized);
            })
            .build();
    }

    @CircuitBreaker(name = "fraudService", fallbackMethod = "scoreFallback")
    public FraudScoringResult scoreTransaction(String transactionId, Long accountId, Long loanId,
                                                String transactionType, BigDecimal amount,
                                                String initiatedBy) {

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("transactionId", transactionId);
        requestBody.put("accountId", accountId);
        requestBody.put("loanId", loanId);
        requestBody.put("transactionType", transactionType);
        requestBody.put("amount", amount);
        requestBody.put("initiatedBy", initiatedBy);

        log.debug("Calling fraud-detection-service for txnId={}", transactionId);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.post()
            .uri("/v1/fraud/score")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofMillis(5000))
            .block();

        return parseResponse(response);
    }

    @SuppressWarnings("unused")
    private FraudScoringResult scoreFallback(String transactionId, Long accountId, Long loanId,
                                              String transactionType, BigDecimal amount,
                                              String initiatedBy, Throwable t) {
        log.warn("Fraud service unavailable for txnId={} ({}), allowing transaction", transactionId, t.getMessage());
        return new FraudScoringResult(0.0, "LOW", "ALLOW", Collections.emptyList(), null);
    }

    @SuppressWarnings("unchecked")
    private FraudScoringResult parseResponse(Map<String, Object> response) {
        if (response == null) {
            return new FraudScoringResult(0.0, "LOW", "ALLOW", Collections.emptyList(), null);
        }

        Map<String, Object> data = response;
        if (response.containsKey("data") && response.get("data") instanceof Map) {
            data = (Map<String, Object>) response.get("data");
        }

        double riskScore = data.get("riskScore") instanceof Number n ? n.doubleValue() : 0.0;
        String riskLevel = (String) data.getOrDefault("riskLevel", "LOW");
        String recommendation = (String) data.getOrDefault("recommendation", "ALLOW");
        var riskFactors = data.get("riskFactors") instanceof java.util.List<?> list
            ? list.stream().map(Object::toString).toList()
            : Collections.<String>emptyList();
        String alertId = (String) data.get("alertId");

        return new FraudScoringResult(riskScore, riskLevel, recommendation, riskFactors, alertId);
    }

    public record FraudScoringResult(
        double riskScore,
        String riskLevel,
        String recommendation,
        java.util.List<String> riskFactors,
        String alertId
    ) {}

    private String resolveBearerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth && jwtAuth.getToken() != null) {
            String tokenValue = jwtAuth.getToken().getTokenValue();
            if (tokenValue != null && !tokenValue.isBlank()) {
                return tokenValue;
            }
        }
        return machineTokenService.getAccessToken();
    }
}
