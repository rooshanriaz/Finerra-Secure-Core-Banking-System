package com.fyp.cbc.client;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fyp.cbc.dto.request.CreateSavingsAccountRequest;
import com.fyp.cbc.dto.response.SavingsAccountResponse;
import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.exception.ResourceNotFoundException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import reactor.core.publisher.Mono;

/**
 * Client for Fineract Savings Account APIs.
 * Handles account management operations.
 */
@Component
public class SavingsAccountClient {
    
    private static final Logger log = LoggerFactory.getLogger(SavingsAccountClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "fineractSavings";
    
    private final WebClient fineractWebClient;
    private final AuthenticationClient authClient;
    
    public SavingsAccountClient(
            @Qualifier("fineractWebClient") WebClient fineractWebClient,
            AuthenticationClient authClient) {
        this.fineractWebClient = fineractWebClient;
        this.authClient = authClient;
    }
    
    /**
     * Create a new savings account for a client.
     * POST /v1/savingsaccounts
     * 
     * @param request Savings account creation details
     * @return Created savings account response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "createAccountFallback")
    @Retry(name = "fineractApi")
    public SavingsAccountResponse createSavingsAccount(CreateSavingsAccountRequest request) {
        log.debug("Creating savings account for client: {}", request.getClientId());
        
        return fineractWebClient.post()
            .uri("/v1/savingsaccounts")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Failed to create savings account: {}", body);
                        return Mono.error(new FineractApiException(
                            "Failed to create savings account: " + body,
                            response.statusCode().value()));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while creating savings account",
                    response.statusCode().value())))
            .bodyToMono(SavingsAccountResponse.class)
            .block();
    }
    
    /**
     * Retrieve savings account details by ID.
     * GET /v1/savingsaccounts/{accountId}
     * 
     * @param accountId The savings account ID
     * @return Savings account details
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getAccountFallback")
    @Retry(name = "fineractApi")
    public SavingsAccountResponse getSavingsAccount(Long accountId) {
        log.debug("Retrieving savings account: {}", accountId);
        
        return fineractWebClient.get()
            .uri("/v1/savingsaccounts/{accountId}", accountId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Savings account not found: {}", accountId);
                return Mono.error(new ResourceNotFoundException("SavingsAccount", accountId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve savings account",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving savings account",
                    response.statusCode().value())))
            .bodyToMono(SavingsAccountResponse.class)
            .block();
    }
    
    /**
     * Update savings account.
     * PUT /v1/savingsaccounts/{accountId}
     * 
     * @param accountId The savings account ID
     * @param request Updated account details
     * @return Updated savings account response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "updateAccountFallback")
    @Retry(name = "fineractApi")
    public SavingsAccountResponse updateSavingsAccount(Long accountId, CreateSavingsAccountRequest request) {
        log.debug("Updating savings account: {}", accountId);
        
        return fineractWebClient.put()
            .uri("/v1/savingsaccounts/{accountId}", accountId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Savings account not found for update: {}", accountId);
                return Mono.error(new ResourceNotFoundException("SavingsAccount", accountId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to update savings account: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while updating savings account",
                    response.statusCode().value())))
            .bodyToMono(SavingsAccountResponse.class)
            .block();
    }
    
    /**
     * Search savings accounts.
     * GET /v1/savingsaccounts
     * 
     * @param searchQuery Optional search query
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return List of savings accounts
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "searchAccountsFallback")
    @Retry(name = "fineractApi")
    public List<SavingsAccountResponse> searchSavingsAccounts(String searchQuery, Integer offset, Integer limit) {
        log.debug("Searching savings accounts: query={}, offset={}, limit={}", searchQuery, offset, limit);
        
        return fineractWebClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/v1/savingsaccounts");
                if (searchQuery != null && !searchQuery.isBlank()) {
                    uriBuilder.queryParam("sqlSearch", searchQuery);
                }
                if (offset != null) {
                    uriBuilder.queryParam("offset", offset);
                }
                if (limit != null) {
                    uriBuilder.queryParam("limit", limit);
                }
                return uriBuilder.build();
            })
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to search savings accounts",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while searching savings accounts",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<List<SavingsAccountResponse>>() {})
            .block();
    }
    
    /**
     * Approve a savings account.
     * POST /v1/savingsaccounts/{accountId}?command=approve
     * 
     * @param accountId The savings account ID
     * @param approvedOnDate Date of approval
     * @return Approval response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "approveAccountFallback")
    @Retry(name = "fineractApi")
    public Object approveSavingsAccount(Long accountId, String approvedOnDate) {
        log.debug("Approving savings account: {}", accountId);
        
        Map<String, Object> request = Map.of(
            "approvedOnDate", approvedOnDate,
            "locale", "en",
            "dateFormat", "dd MMMM yyyy"
        );
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/savingsaccounts/{accountId}")
                .queryParam("command", "approve")
                .build(accountId))
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Savings account not found for approval: {}", accountId);
                return Mono.error(new ResourceNotFoundException("SavingsAccount", accountId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to approve savings account: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while approving savings account",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * Activate a savings account.
     * POST /v1/savingsaccounts/{accountId}?command=activate
     * 
     * @param accountId The savings account ID
     * @param activatedOnDate Date of activation
     * @return Activation response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "activateAccountFallback")
    @Retry(name = "fineractApi")
    public Object activateSavingsAccount(Long accountId, String activatedOnDate) {
        log.debug("Activating savings account: {}", accountId);
        
        Map<String, Object> request = Map.of(
            "activatedOnDate", activatedOnDate,
            "locale", "en",
            "dateFormat", "dd MMMM yyyy"
        );
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/savingsaccounts/{accountId}")
                .queryParam("command", "activate")
                .build(accountId))
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Savings account not found for activation: {}", accountId);
                return Mono.error(new ResourceNotFoundException("SavingsAccount", accountId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to activate savings account: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while activating savings account",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    // Fallback methods
    
    private SavingsAccountResponse createAccountFallback(CreateSavingsAccountRequest request, Throwable t) {
        log.error("Circuit breaker fallback for createSavingsAccount: {}", t.getMessage());
        throw new FineractApiException(
            "Savings account service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private SavingsAccountResponse getAccountFallback(Long accountId, Throwable t) {
        log.error("Circuit breaker fallback for getSavingsAccount: {}", t.getMessage());
        throw new FineractApiException(
            "Savings account service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private SavingsAccountResponse updateAccountFallback(Long accountId, CreateSavingsAccountRequest request, Throwable t) {
        log.error("Circuit breaker fallback for updateSavingsAccount: {}", t.getMessage());
        throw new FineractApiException(
            "Savings account service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private List<SavingsAccountResponse> searchAccountsFallback(String searchQuery, Integer offset, Integer limit, Throwable t) {
        log.error("Circuit breaker fallback for searchSavingsAccounts: {}", t.getMessage());
        throw new FineractApiException(
            "Savings account service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private Object approveAccountFallback(Long accountId, String approvedOnDate, Throwable t) {
        log.error("Circuit breaker fallback for approveSavingsAccount: {}", t.getMessage());
        throw new FineractApiException(
            "Savings account service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private Object activateAccountFallback(Long accountId, String activatedOnDate, Throwable t) {
        log.error("Circuit breaker fallback for activateSavingsAccount: {}", t.getMessage());
        throw new FineractApiException(
            "Savings account service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
}
