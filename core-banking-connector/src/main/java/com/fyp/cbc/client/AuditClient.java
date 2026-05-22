package com.fyp.cbc.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fyp.cbc.dto.response.AuditResponse;
import com.fyp.cbc.dto.response.AuditResponse.AuditSearchCriteria;
import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.exception.ResourceNotFoundException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import reactor.core.publisher.Mono;

/**
 * Client for Fineract Audit and Reports APIs.
 * Handles audit logs, reports, and compliance monitoring.
 */
@Component
public class AuditClient {
    
    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "fineractAudit";
    
    private final WebClient fineractWebClient;
    private final AuthenticationClient authClient;
    
    public AuditClient(
            @Qualifier("fineractWebClient") WebClient fineractWebClient,
            AuthenticationClient authClient) {
        this.fineractWebClient = fineractWebClient;
        this.authClient = authClient;
    }
    
    /**
     * Search audit logs.
     * GET /v1/audits
     * 
     * @param criteria Search criteria for audits
     * @return Audit response with matching entries
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "searchAuditsFallback")
    @Retry(name = "fineractApi")
    public AuditResponse searchAudits(AuditSearchCriteria criteria) {
        log.debug("Searching audits with criteria: {}", criteria);
        
        return fineractWebClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/v1/audits");
                if (criteria != null) {
                    if (criteria.getActionName() != null) {
                        uriBuilder.queryParam("actionName", criteria.getActionName());
                    }
                    if (criteria.getEntityName() != null) {
                        uriBuilder.queryParam("entityName", criteria.getEntityName());
                    }
                    if (criteria.getResourceId() != null) {
                        uriBuilder.queryParam("resourceId", criteria.getResourceId());
                    }
                    if (criteria.getMakerId() != null) {
                        uriBuilder.queryParam("makerId", criteria.getMakerId());
                    }
                    if (criteria.getMakerDateTimeFrom() != null) {
                        uriBuilder.queryParam("makerDateTimeFrom", criteria.getMakerDateTimeFrom());
                    }
                    if (criteria.getMakerDateTimeTo() != null) {
                        uriBuilder.queryParam("makerDateTimeTo", criteria.getMakerDateTimeTo());
                    }
                    if (criteria.getOfficeId() != null) {
                        uriBuilder.queryParam("officeId", criteria.getOfficeId());
                    }
                    if (criteria.getLoanId() != null) {
                        uriBuilder.queryParam("loanId", criteria.getLoanId());
                    }
                    if (criteria.getSavingsAccountId() != null) {
                        uriBuilder.queryParam("savingsAccountId", criteria.getSavingsAccountId());
                    }
                    if (criteria.getOffset() != null) {
                        uriBuilder.queryParam("offset", criteria.getOffset());
                    }
                    if (criteria.getLimit() != null) {
                        uriBuilder.queryParam("limit", criteria.getLimit());
                    }
                    if (criteria.getOrderBy() != null) {
                        uriBuilder.queryParam("orderBy", criteria.getOrderBy());
                    }
                    if (criteria.getSortOrder() != null) {
                        uriBuilder.queryParam("sortOrder", criteria.getSortOrder());
                    }
                }
                return uriBuilder.build();
            })
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to search audits",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while searching audits",
                    response.statusCode().value())))
            .bodyToMono(AuditResponse.class)
            .block();
    }
    
    /**
     * Retrieve specific audit entry by ID.
     * GET /v1/audits/{auditId}
     * 
     * @param auditId The audit entry ID
     * @return Audit entry details
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getAuditFallback")
    @Retry(name = "fineractApi")
    public AuditResponse.AuditData getAudit(Long auditId) {
        log.debug("Retrieving audit entry: {}", auditId);
        
        return fineractWebClient.get()
            .uri("/v1/audits/{auditId}", auditId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Audit entry not found: {}", auditId);
                return Mono.error(new ResourceNotFoundException("Audit", auditId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve audit entry",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving audit entry",
                    response.statusCode().value())))
            .bodyToMono(AuditResponse.AuditData.class)
            .block();
    }
    
    /**
     * List available reports.
     * GET /v1/reports
     * 
     * @return List of available reports
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "listReportsFallback")
    @Retry(name = "fineractApi")
    public List<Object> listReports() {
        log.debug("Listing available reports");
        
        return fineractWebClient.get()
            .uri("/v1/reports")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to list reports",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while listing reports",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<List<Object>>() {})
            .block();
    }
    
    /**
     * Run a specific report.
     * GET /v1/reports/{reportName}
     * 
     * @param reportName Name of the report to run
     * @param parameters Optional report parameters
     * @return Report results
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "runReportFallback")
    @Retry(name = "fineractApi")
    public Object runReport(String reportName, java.util.Map<String, Object> parameters) {
        log.debug("Running report: {}", reportName);
        
        return fineractWebClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/v1/reports/{reportName}");
                if (parameters != null) {
                    parameters.forEach(uriBuilder::queryParam);
                }
                return uriBuilder.build(reportName);
            })
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Report not found: {}", reportName);
                return Mono.error(new ResourceNotFoundException("Report", reportName));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to run report",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while running report",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * Create a custom report template.
     * POST /v1/reports
     * 
     * @param reportDefinition Report template definition
     * @return Created report response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "createReportFallback")
    @Retry(name = "fineractApi")
    public Object createReport(Object reportDefinition) {
        log.debug("Creating custom report");
        
        return fineractWebClient.post()
            .uri("/v1/reports")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(reportDefinition)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to create report: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while creating report",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * Get audits for a specific user.
     * 
     * @param userId User ID to filter audits
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return Audit entries for the user
     */
    public AuditResponse getAuditsByUser(String userId, Integer offset, Integer limit) {
        AuditSearchCriteria criteria = AuditSearchCriteria.builder()
            .makerId(userId)
            .offset(offset)
            .limit(limit)
            .orderBy("madeOnDate")
            .sortOrder("DESC")
            .build();
        return searchAudits(criteria);
    }
    
    /**
     * Get audits for a specific action.
     * 
     * @param actionName Action name to filter (e.g., "CREATE", "UPDATE", "DELETE")
     * @param entityName Entity name to filter (e.g., "CLIENT", "LOAN", "SAVINGSACCOUNT")
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return Audit entries matching criteria
     */
    public AuditResponse getAuditsByAction(String actionName, String entityName, Integer offset, Integer limit) {
        AuditSearchCriteria criteria = AuditSearchCriteria.builder()
            .actionName(actionName)
            .entityName(entityName)
            .offset(offset)
            .limit(limit)
            .orderBy("madeOnDate")
            .sortOrder("DESC")
            .build();
        return searchAudits(criteria);
    }
    
    /**
     * Get audits within a date range.
     * 
     * @param fromDate Start date (format: yyyy-MM-dd HH:mm:ss)
     * @param toDate End date (format: yyyy-MM-dd HH:mm:ss)
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return Audit entries in date range
     */
    public AuditResponse getAuditsByDateRange(String fromDate, String toDate, Integer offset, Integer limit) {
        AuditSearchCriteria criteria = AuditSearchCriteria.builder()
            .makerDateTimeFrom(fromDate)
            .makerDateTimeTo(toDate)
            .offset(offset)
            .limit(limit)
            .orderBy("madeOnDate")
            .sortOrder("DESC")
            .build();
        return searchAudits(criteria);
    }
    
    // Fallback methods
    
    private AuditResponse searchAuditsFallback(AuditSearchCriteria criteria, Throwable t) {
        log.error("Circuit breaker fallback for searchAudits: {}", t.getMessage());
        throw new FineractApiException(
            "Audit service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private AuditResponse.AuditData getAuditFallback(Long auditId, Throwable t) {
        log.error("Circuit breaker fallback for getAudit: {}", t.getMessage());
        throw new FineractApiException(
            "Audit service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private List<Object> listReportsFallback(Throwable t) {
        log.error("Circuit breaker fallback for listReports: {}", t.getMessage());
        throw new FineractApiException(
            "Report service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private Object runReportFallback(String reportName, java.util.Map<String, Object> parameters, Throwable t) {
        log.error("Circuit breaker fallback for runReport: {}", t.getMessage());
        throw new FineractApiException(
            "Report service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private Object createReportFallback(Object reportDefinition, Throwable t) {
        log.error("Circuit breaker fallback for createReport: {}", t.getMessage());
        throw new FineractApiException(
            "Report service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
}
