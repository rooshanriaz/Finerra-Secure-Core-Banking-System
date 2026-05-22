package com.fyp.cbc.client;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fyp.cbc.dto.request.TransactionRequest;
import com.fyp.cbc.dto.response.TransactionResponse;
import com.fyp.cbc.dto.response.TransactionResponse.JournalEntryResponse;
import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.exception.ResourceNotFoundException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import reactor.core.publisher.Mono;

/**
 * Client for Fineract Transaction APIs.
 * Handles deposits, withdrawals, loan repayments, and journal entries.
 */
@Component
public class TransactionClient {
    
    private static final Logger log = LoggerFactory.getLogger(TransactionClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "fineractTransaction";
    
    private final WebClient fineractWebClient;
    private final AuthenticationClient authClient;
    
    public TransactionClient(
            @Qualifier("fineractWebClient") WebClient fineractWebClient,
            AuthenticationClient authClient) {
        this.fineractWebClient = fineractWebClient;
        this.authClient = authClient;
    }
    
    /**
     * Deposit funds to a savings account.
     * POST /v1/savingsaccounts/{accountId}/transactions?command=deposit
     * 
     * @param accountId The savings account ID
     * @param request Transaction details
     * @return Transaction response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "depositFallback")
    @Retry(name = "fineractApi")
    public TransactionResponse deposit(Long accountId, TransactionRequest request) {
        log.debug("Depositing {} to account: {}", request.getTransactionAmount(), accountId);
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/savingsaccounts/{accountId}/transactions")
                .queryParam("command", "deposit")
                .build(accountId))
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Savings account not found for deposit: {}", accountId);
                return Mono.error(new ResourceNotFoundException("SavingsAccount", accountId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Deposit failed: {}", body);
                        return Mono.error(new FineractApiException(
                            "Failed to process deposit: " + body,
                            response.statusCode().value()));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while processing deposit",
                    response.statusCode().value())))
            .bodyToMono(TransactionResponse.class)
            .block();
    }
    
    /**
     * Simplified deposit method with just amount.
     * 
     * @param accountId The savings account ID
     * @param amount Amount to deposit
     * @param transactionDate Date of transaction (format: dd MMMM yyyy)
     * @return Transaction response
     */
    public TransactionResponse deposit(Long accountId, BigDecimal amount, String transactionDate) {
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(amount)
            .transactionDate(transactionDate)
            .build();
        return deposit(accountId, request);
    }
    
    /**
     * Withdraw funds from a savings account.
     * POST /v1/savingsaccounts/{accountId}/transactions?command=withdrawal
     * 
     * @param accountId The savings account ID
     * @param request Transaction details
     * @return Transaction response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "withdrawalFallback")
    @Retry(name = "fineractApi")
    public TransactionResponse withdraw(Long accountId, TransactionRequest request) {
        log.debug("Withdrawing {} from account: {}", request.getTransactionAmount(), accountId);
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/savingsaccounts/{accountId}/transactions")
                .queryParam("command", "withdrawal")
                .build(accountId))
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Savings account not found for withdrawal: {}", accountId);
                return Mono.error(new ResourceNotFoundException("SavingsAccount", accountId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Withdrawal failed: {}", body);
                        return Mono.error(new FineractApiException(
                            "Failed to process withdrawal: " + body,
                            response.statusCode().value()));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while processing withdrawal",
                    response.statusCode().value())))
            .bodyToMono(TransactionResponse.class)
            .block();
    }
    
    /**
     * Simplified withdrawal method with just amount.
     * 
     * @param accountId The savings account ID
     * @param amount Amount to withdraw
     * @param transactionDate Date of transaction (format: dd MMMM yyyy)
     * @return Transaction response
     */
    public TransactionResponse withdraw(Long accountId, BigDecimal amount, String transactionDate) {
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(amount)
            .transactionDate(transactionDate)
            .build();
        return withdraw(accountId, request);
    }
    
    /**
     * Make a loan repayment.
     * POST /v1/loans/{loanId}/transactions?command=repayment
     * 
     * @param loanId The loan ID
     * @param request Transaction details
     * @return Transaction response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "repaymentFallback")
    @Retry(name = "fineractApi")
    public TransactionResponse loanRepayment(Long loanId, TransactionRequest request) {
        log.debug("Processing loan repayment of {} for loan: {}", request.getTransactionAmount(), loanId);
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/loans/{loanId}/transactions")
                .queryParam("command", "repayment")
                .build(loanId))
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Loan not found for repayment: {}", loanId);
                return Mono.error(new ResourceNotFoundException("Loan", loanId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Loan repayment failed: {}", body);
                        return Mono.error(new FineractApiException(
                            "Failed to process loan repayment: " + body,
                            response.statusCode().value()));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while processing loan repayment",
                    response.statusCode().value())))
            .bodyToMono(TransactionResponse.class)
            .block();
    }
    
    /**
     * Simplified loan repayment method.
     * 
     * @param loanId The loan ID
     * @param amount Repayment amount
     * @param transactionDate Date of transaction
     * @return Transaction response
     */
    public TransactionResponse loanRepayment(Long loanId, BigDecimal amount, String transactionDate) {
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(amount)
            .transactionDate(transactionDate)
            .build();
        return loanRepayment(loanId, request);
    }
    
    /**
     * Retrieve journal entries (transaction logs).
     * GET /v1/journalentries
     * 
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @param officeId Optional office filter
     * @param glAccountId Optional GL account filter
     * @param manualEntriesOnly Filter for manual entries only
     * @return Journal entries response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getJournalEntriesFallback")
    @Retry(name = "fineractApi")
    public JournalEntryResponse getJournalEntries(Integer offset, Integer limit, Long officeId, 
            Long glAccountId, Boolean manualEntriesOnly) {
        log.debug("Retrieving journal entries: offset={}, limit={}", offset, limit);
        
        return fineractWebClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/v1/journalentries");
                if (offset != null) {
                    uriBuilder.queryParam("offset", offset);
                }
                if (limit != null) {
                    uriBuilder.queryParam("limit", limit);
                }
                if (officeId != null) {
                    uriBuilder.queryParam("officeId", officeId);
                }
                if (glAccountId != null) {
                    uriBuilder.queryParam("glAccountId", glAccountId);
                }
                if (manualEntriesOnly != null) {
                    uriBuilder.queryParam("manualEntriesOnly", manualEntriesOnly);
                }
                return uriBuilder.build();
            })
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve journal entries",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving journal entries",
                    response.statusCode().value())))
            .bodyToMono(JournalEntryResponse.class)
            .block();
    }
    
    /**
     * Get specific transaction details by transaction ID.
     * GET /v1/journalentries?transactionId={transactionId}
     * 
     * @param transactionId The transaction ID
     * @return Journal entries for the transaction
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getTransactionDetailsFallback")
    @Retry(name = "fineractApi")
    public JournalEntryResponse getTransactionDetails(String transactionId) {
        log.debug("Retrieving transaction details: {}", transactionId);
        
        return fineractWebClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/journalentries")
                .queryParam("transactionId", transactionId)
                .build())
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve transaction details",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving transaction details",
                    response.statusCode().value())))
            .bodyToMono(JournalEntryResponse.class)
            .block();
    }
    
    // Fallback methods
    
    private TransactionResponse depositFallback(Long accountId, TransactionRequest request, Throwable t) {
        log.error("Circuit breaker fallback for deposit: {}", t.getMessage());
        throw new FineractApiException(
            "Transaction service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private TransactionResponse withdrawalFallback(Long accountId, TransactionRequest request, Throwable t) {
        log.error("Circuit breaker fallback for withdrawal: {}", t.getMessage());
        throw new FineractApiException(
            "Transaction service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private TransactionResponse repaymentFallback(Long loanId, TransactionRequest request, Throwable t) {
        log.error("Circuit breaker fallback for loan repayment: {}", t.getMessage());
        throw new FineractApiException(
            "Transaction service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private JournalEntryResponse getJournalEntriesFallback(Integer offset, Integer limit, Long officeId, 
            Long glAccountId, Boolean manualEntriesOnly, Throwable t) {
        log.error("Circuit breaker fallback for getJournalEntries: {}", t.getMessage());
        throw new FineractApiException(
            "Transaction service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private JournalEntryResponse getTransactionDetailsFallback(String transactionId, Throwable t) {
        log.error("Circuit breaker fallback for getTransactionDetails: {}", t.getMessage());
        throw new FineractApiException(
            "Transaction service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
}
