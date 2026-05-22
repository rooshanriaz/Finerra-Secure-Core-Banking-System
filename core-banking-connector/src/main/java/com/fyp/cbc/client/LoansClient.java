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

import com.fyp.cbc.dto.request.CreateLoanRequest;
import com.fyp.cbc.dto.request.RescheduleRequest;
import com.fyp.cbc.dto.response.LoanResponse;
import com.fyp.cbc.dto.response.PagedResponse;
import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.exception.ResourceNotFoundException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import reactor.core.publisher.Mono;

/**
 * Client for Fineract Loans APIs.
 * Handles loan applications, approvals, disbursements, and rescheduling.
 */
@Component
public class LoansClient {
    
    private static final Logger log = LoggerFactory.getLogger(LoansClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "fineractLoan";
    
    private final WebClient fineractWebClient;
    private final AuthenticationClient authClient;
    
    public LoansClient(
            @Qualifier("fineractWebClient") WebClient fineractWebClient,
            AuthenticationClient authClient) {
        this.fineractWebClient = fineractWebClient;
        this.authClient = authClient;
    }
    
    /**
     * Create a new loan application.
     * POST /v1/loans
     * 
     * @param request Loan creation details
     * @return Created loan response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "createLoanFallback")
    @Retry(name = "fineractApi")
    public LoanResponse createLoan(CreateLoanRequest request) {
        log.debug("Creating loan for client: {}", request.getClientId());
        
        return fineractWebClient.post()
            .uri("/v1/loans")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Failed to create loan: {}", body);
                        return Mono.error(new FineractApiException(
                            "Failed to create loan: " + body,
                            response.statusCode().value()));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while creating loan",
                    response.statusCode().value())))
            .bodyToMono(LoanResponse.class)
            .block();
    }
    
    /**
     * Retrieve loan details by ID.
     * GET /v1/loans/{loanId}
     * 
     * @param loanId The loan ID
     * @return Loan details
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getLoanFallback")
    @Retry(name = "fineractApi")
    public LoanResponse getLoan(Long loanId) {
        log.debug("Retrieving loan: {}", loanId);
        
        return fineractWebClient.get()
            .uri("/v1/loans/{loanId}", loanId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Loan not found: {}", loanId);
                return Mono.error(new ResourceNotFoundException("Loan", loanId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve loan",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving loan",
                    response.statusCode().value())))
            .bodyToMono(LoanResponse.class)
            .block();
    }
    
    /**
     * Update loan details.
     * PUT /v1/loans/{loanId}
     * 
     * @param loanId The loan ID
     * @param request Updated loan details
     * @return Updated loan response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "updateLoanFallback")
    @Retry(name = "fineractApi")
    public LoanResponse updateLoan(Long loanId, CreateLoanRequest request) {
        log.debug("Updating loan: {}", loanId);
        
        return fineractWebClient.put()
            .uri("/v1/loans/{loanId}", loanId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Loan not found for update: {}", loanId);
                return Mono.error(new ResourceNotFoundException("Loan", loanId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to update loan: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while updating loan",
                    response.statusCode().value())))
            .bodyToMono(LoanResponse.class)
            .block();
    }
    
    /**
     * Approve a loan application.
     * POST /v1/loans/{loanId}?command=approve
     * 
     * @param loanId The loan ID
     * @param approvedOnDate Date of approval
     * @param approvedLoanAmount Approved amount (optional, uses requested if null)
     * @param note Optional approval note
     * @return Approval response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "approveLoanFallback")
    @Retry(name = "fineractApi")
    public Object approveLoan(Long loanId, String approvedOnDate, java.math.BigDecimal approvedLoanAmount, String note) {
        log.debug("Approving loan: {}", loanId);
        
        java.util.HashMap<String, Object> request = new java.util.HashMap<>();
        request.put("approvedOnDate", approvedOnDate);
        request.put("locale", "en");
        request.put("dateFormat", "dd MMMM yyyy");
        if (approvedLoanAmount != null) {
            request.put("approvedLoanAmount", approvedLoanAmount);
        }
        if (note != null) {
            request.put("note", note);
        }
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/loans/{loanId}")
                .queryParam("command", "approve")
                .build(loanId))
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Loan not found for approval: {}", loanId);
                return Mono.error(new ResourceNotFoundException("Loan", loanId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to approve loan: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while approving loan",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * Disburse an approved loan.
     * POST /v1/loans/{loanId}?command=disburse
     * 
     * @param loanId The loan ID
     * @param actualDisbursementDate Date of disbursement
     * @param transactionAmount Disbursement amount
     * @return Disbursement response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "disburseLoanFallback")
    @Retry(name = "fineractApi")
    public Object disburseLoan(Long loanId, String actualDisbursementDate, java.math.BigDecimal transactionAmount) {
        log.debug("Disbursing loan: {}", loanId);
        
        java.util.HashMap<String, Object> request = new java.util.HashMap<>();
        request.put("actualDisbursementDate", actualDisbursementDate);
        request.put("locale", "en");
        request.put("dateFormat", "dd MMMM yyyy");
        if (transactionAmount != null) {
            request.put("transactionAmount", transactionAmount);
        }
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/loans/{loanId}")
                .queryParam("command", "disburse")
                .build(loanId))
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Loan not found for disbursement: {}", loanId);
                return Mono.error(new ResourceNotFoundException("Loan", loanId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to disburse loan: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while disbursing loan",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * Reject a loan application.
     * POST /v1/loans/{loanId}?command=reject
     * 
     * @param loanId The loan ID
     * @param rejectedOnDate Date of rejection
     * @param note Rejection reason
     * @return Rejection response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "rejectLoanFallback")
    @Retry(name = "fineractApi")
    public Object rejectLoan(Long loanId, String rejectedOnDate, String note) {
        log.debug("Rejecting loan: {}", loanId);
        
        Map<String, Object> request = Map.of(
            "rejectedOnDate", rejectedOnDate,
            "locale", "en",
            "dateFormat", "dd MMMM yyyy",
            "note", note != null ? note : "Loan application rejected"
        );
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/loans/{loanId}")
                .queryParam("command", "reject")
                .build(loanId))
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Loan not found for rejection: {}", loanId);
                return Mono.error(new ResourceNotFoundException("Loan", loanId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to reject loan: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while rejecting loan",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * Search loans.
     * GET /v1/loans
     * 
     * @param searchQuery Optional search query
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return List of loans
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "searchLoansFallback")
    @Retry(name = "fineractApi")
    public List<LoanResponse> searchLoans(String searchQuery, Integer offset, Integer limit) {
        log.debug("Searching loans: query={}, offset={}, limit={}", searchQuery, offset, limit);
        
        PagedResponse<LoanResponse> pagedResponse = fineractWebClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/v1/loans");
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
                    "Failed to search loans",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while searching loans",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<PagedResponse<LoanResponse>>() {})
            .block();
        
        return pagedResponse != null && pagedResponse.getPageItems() != null
            ? pagedResponse.getPageItems()
            : java.util.Collections.emptyList();
    }
    
    /**
     * Get loan transactions.
     * GET /v1/loans/{loanId}/transactions
     * 
     * @param loanId The loan ID
     * @return List of loan transactions
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getLoanTransactionsFallback")
    @Retry(name = "fineractApi")
    public List<Object> getLoanTransactions(Long loanId) {
        log.debug("Retrieving loan transactions: {}", loanId);
        
        return fineractWebClient.get()
            .uri("/v1/loans/{loanId}/transactions", loanId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Loan not found: {}", loanId);
                return Mono.error(new ResourceNotFoundException("Loan", loanId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve loan transactions",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving loan transactions",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<List<Object>>() {})
            .block();
    }

    /**
     * List loan products available in Fineract.
     * GET /v1/loanproducts
     *
     * @return list of loan products
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getLoanProductsFallback")
    @Retry(name = "fineractApi")
    public List<Object> getLoanProducts() {
        log.debug("Retrieving loan products");

        return fineractWebClient.get()
            .uri("/v1/loanproducts")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve loan products",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving loan products",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<List<Object>>() {})
            .block();
    }

    /**
     * Create a new loan product in Fineract.
     * POST /v1/loanproducts
     *
     * @param request loan product payload
     * @return created loan product response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "createLoanProductFallback")
    @Retry(name = "fineractApi")
    public Object createLoanProduct(Map<String, Object> request) {
        log.info("Creating loan product: {}", request.get("name"));

        return fineractWebClient.post()
            .uri("/v1/loanproducts")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to create loan product: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while creating loan product",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    // ============ Reschedule Loans APIs ============
    
    /**
     * Get all reschedule requests.
     * GET /v1/rescheduleloans
     * 
     * @return List of reschedule requests
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getRescheduleRequestsFallback")
    @Retry(name = "fineractApi")
    public List<Object> getRescheduleRequests() {
        log.debug("Retrieving all reschedule requests");
        
        return fineractWebClient.get()
            .uri("/v1/rescheduleloans")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve reschedule requests",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving reschedule requests",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<List<Object>>() {})
            .block();
    }
    
    /**
     * Create a reschedule request.
     * POST /v1/rescheduleloans
     * 
     * @param request Reschedule request details
     * @return Created reschedule request
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "createRescheduleFallback")
    @Retry(name = "fineractApi")
    public Object createRescheduleRequest(RescheduleRequest request) {
        log.debug("Creating reschedule request for loan: {}", request.getLoanId());
        
        return fineractWebClient.post()
            .uri("/v1/rescheduleloans")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to create reschedule request: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while creating reschedule request",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * Get reschedule request by ID.
     * GET /v1/rescheduleloans/{scheduleId}
     * 
     * @param scheduleId The reschedule request ID
     * @return Reschedule request details
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getRescheduleByIdFallback")
    @Retry(name = "fineractApi")
    public Object getRescheduleRequest(Long scheduleId) {
        log.debug("Retrieving reschedule request: {}", scheduleId);
        
        return fineractWebClient.get()
            .uri("/v1/rescheduleloans/{scheduleId}", scheduleId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Reschedule request not found: {}", scheduleId);
                return Mono.error(new ResourceNotFoundException("RescheduleRequest", scheduleId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve reschedule request",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving reschedule request",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    // Fallback methods
    
    private LoanResponse createLoanFallback(CreateLoanRequest request, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "createLoan");
    }

    private LoanResponse getLoanFallback(Long loanId, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "getLoan");
    }

    private LoanResponse updateLoanFallback(Long loanId, CreateLoanRequest request, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "updateLoan");
    }

    private Object approveLoanFallback(Long loanId, String approvedOnDate, java.math.BigDecimal approvedLoanAmount, String note, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "approveLoan");
    }

    private Object disburseLoanFallback(Long loanId, String actualDisbursementDate, java.math.BigDecimal transactionAmount, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "disburseLoan");
    }

    private Object rejectLoanFallback(Long loanId, String rejectedOnDate, String note, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "rejectLoan");
    }

    private List<LoanResponse> searchLoansFallback(String searchQuery, Integer offset, Integer limit, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "searchLoans");
    }

    private List<Object> getLoanTransactionsFallback(Long loanId, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "getLoanTransactions");
    }

    private List<Object> getLoanProductsFallback(Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "getLoanProducts");
    }

    private Object createLoanProductFallback(Map<String, Object> request, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "createLoanProduct");
    }

    private List<Object> getRescheduleRequestsFallback(Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "getRescheduleRequests");
    }

    private Object createRescheduleFallback(RescheduleRequest request, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "createRescheduleRequest");
    }

    private Object getRescheduleByIdFallback(Long scheduleId, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "getRescheduleRequest");
    }
}
