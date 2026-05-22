package com.fyp.txn.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for Core Banking Connector (CBC) service.
 * Proxies transaction operations to Fineract via CBC.
 */
@Slf4j
@Service
public class CbcClientService {

    private final WebClient webClient;
    private final Long defaultPaymentTypeId;
    private final MachineTokenService machineTokenService;

    public CbcClientService(WebClient.Builder webClientBuilder,
                            @Value("${services.core-banking-connector.url}") String cbcUrl,
                            MachineTokenService machineTokenService,
                            @Value("${fineract.default-payment-type-id:1}") Long defaultPaymentTypeId) {
        this.machineTokenService = machineTokenService;
        this.webClient = webClientBuilder
            .baseUrl(cbcUrl)
            .filter((request, next) -> {
                String token = resolveBearerToken();
                var authorized = org.springframework.web.reactive.function.client.ClientRequest.from(request)
                        .headers(headers -> headers.setBearerAuth(token))
                        .build();
                return next.exchange(authorized);
            })
            .build();
        this.defaultPaymentTypeId = defaultPaymentTypeId;
        log.info("CBC client configured with URL: {}, defaultPaymentTypeId: {}", cbcUrl, defaultPaymentTypeId);
    }

    /**
     * Execute a deposit via CBC -> Fineract.
     */
    @CircuitBreaker(name = "cbcService", fallbackMethod = "transactionFallback")
    public Map<String, Object> deposit(Long accountId, BigDecimal amount, String transactionDate, String note) {
        log.info("Executing deposit: accountId={}, amount={}", accountId, amount);

        Map<String, Object> payload = buildTransactionPayload(amount, transactionDate, note);

        return webClient.post()
            .uri("/v1/savingsaccounts/{accountId}/transactions?command=deposit", accountId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
    }

    /**
     * Execute a withdrawal via CBC -> Fineract.
     */
    @CircuitBreaker(name = "cbcService", fallbackMethod = "transactionFallback")
    public Map<String, Object> withdraw(Long accountId, BigDecimal amount, String transactionDate, String note) {
        log.info("Executing withdrawal: accountId={}, amount={}", accountId, amount);

        Map<String, Object> payload = buildTransactionPayload(amount, transactionDate, note);

        return webClient.post()
            .uri("/v1/savingsaccounts/{accountId}/transactions?command=withdrawal", accountId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
    }

    /**
     * Execute a loan repayment via CBC -> Fineract.
     */
    @CircuitBreaker(name = "cbcService", fallbackMethod = "transactionFallback")
    public Map<String, Object> loanRepayment(Long loanId, BigDecimal amount, String transactionDate, String note) {
        log.info("Executing loan repayment: loanId={}, amount={}", loanId, amount);

        Map<String, Object> payload = buildTransactionPayload(amount, transactionDate, note);

        return webClient.post()
            .uri("/v1/loans/{loanId}/transactions?command=repayment", loanId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
    }

    /**
     * Get savings account details.
     */
    @CircuitBreaker(name = "cbcService", fallbackMethod = "getAccountFallback")
    public Map<String, Object> getSavingsAccount(Long accountId) {
        log.debug("Getting savings account: {}", accountId);
        return webClient.get()
            .uri("/v1/savingsaccounts/{accountId}", accountId)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
    }

    /**
     * Get loan details.
     */
    @CircuitBreaker(name = "cbcService", fallbackMethod = "getLoanFallback")
    public Map<String, Object> getLoan(Long loanId) {
        log.debug("Getting loan: {}", loanId);
        return webClient.get()
            .uri("/v1/loans/{loanId}", loanId)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
    }

    /**
     * Get journal entries.
     */
    @CircuitBreaker(name = "cbcService", fallbackMethod = "getJournalFallback")
    public Map<String, Object> getJournalEntries(Integer offset, Integer limit) {
        log.debug("Getting journal entries: offset={}, limit={}", offset, limit);
        return webClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/v1/journalentries");
                if (offset != null) uriBuilder.queryParam("offset", offset);
                if (limit != null) uriBuilder.queryParam("limit", limit);
                return uriBuilder.build();
            })
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
    }

    private Map<String, Object> buildTransactionPayload(BigDecimal amount, String transactionDate, String note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionDate", transactionDate);
        payload.put("transactionAmount", amount);
        payload.put("paymentTypeId", defaultPaymentTypeId);
        payload.put("locale", "en");
        payload.put("dateFormat", "dd MMMM yyyy");
        if (note != null && !note.isBlank()) {
            payload.put("note", note);
        }
        return payload;
    }

    // Fallback methods

    @SuppressWarnings("unused")
    private Map<String, Object> transactionFallback(Long id, BigDecimal amount, String date, String note, Throwable t) {
        log.error("CBC transaction fallback: {}", t.getMessage());
        String errorMsg = "Core Banking service unavailable";
        if (t instanceof WebClientResponseException wce) {
            errorMsg = "CBC error: " + wce.getStatusCode() + " - " + wce.getResponseBodyAsString();
        }
        return Map.of("success", false, "message", errorMsg, "error", t.getMessage());
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getAccountFallback(Long accountId, Throwable t) {
        log.error("CBC getSavingsAccount fallback: {}", t.getMessage());
        return Map.of("success", false, "message", "Account service unavailable");
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getLoanFallback(Long loanId, Throwable t) {
        log.error("CBC getLoan fallback: {}", t.getMessage());
        return Map.of("success", false, "message", "Loan service unavailable");
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getJournalFallback(Integer offset, Integer limit, Throwable t) {
        log.error("CBC getJournalEntries fallback: {}", t.getMessage());
        return Map.of("success", false, "message", "Journal entries service unavailable");
    }

    /**
     * Prefer incoming user JWT (contains role context expected by CBC/Fineract).
     * Fallback to machine token for non-request threads.
     */
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
