package com.fyp.cbc.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fyp.cbc.client.AuditClient;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.AuditResponse;
import com.fyp.cbc.dto.response.AuditResponse.AuditSearchCriteria;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for audit and reporting operations.
 * Handles audit logs, compliance monitoring, and report generation.
 */
@Service
@RequiredArgsConstructor
public class AuditService {
    
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    
    private final AuditClient auditClient;
    
    /**
     * Search audit logs.
     * 
     * @param criteria Search criteria for audits
     * @return API response with audit entries
     */
    public ApiResponse<AuditResponse> searchAudits(AuditSearchCriteria criteria) {
        log.debug("Searching audits with criteria: {}", criteria);
        
        AuditResponse response = auditClient.searchAudits(criteria);
        return ApiResponse.success(response);
    }
    
    /**
     * Retrieve specific audit entry by ID.
     * 
     * @param auditId The audit entry ID
     * @return API response with audit details
     */
    public ApiResponse<AuditResponse.AuditData> getAudit(Long auditId) {
        log.debug("Retrieving audit entry: {}", auditId);
        
        AuditResponse.AuditData response = auditClient.getAudit(auditId);
        return ApiResponse.success(response);
    }
    
    /**
     * Get audits for a specific user.
     * 
     * @param userId User ID to filter audits
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return API response with user's audit entries
     */
    public ApiResponse<AuditResponse> getAuditsByUser(String userId, Integer offset, Integer limit) {
        log.debug("Retrieving audits for user: {}", userId);
        
        AuditResponse response = auditClient.getAuditsByUser(userId, offset, limit);
        return ApiResponse.success(response);
    }
    
    /**
     * Get audits for a specific action.
     * 
     * @param actionName Action name (e.g., "CREATE", "UPDATE", "DELETE")
     * @param entityName Entity name (e.g., "CLIENT", "LOAN")
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return API response with matching audit entries
     */
    public ApiResponse<AuditResponse> getAuditsByAction(String actionName, String entityName, 
            Integer offset, Integer limit) {
        log.debug("Retrieving audits for action: {} on entity: {}", actionName, entityName);
        
        AuditResponse response = auditClient.getAuditsByAction(actionName, entityName, offset, limit);
        return ApiResponse.success(response);
    }
    
    /**
     * Get audits within a date range.
     * 
     * @param fromDate Start date (format: yyyy-MM-dd HH:mm:ss)
     * @param toDate End date (format: yyyy-MM-dd HH:mm:ss)
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return API response with audit entries in date range
     */
    public ApiResponse<AuditResponse> getAuditsByDateRange(String fromDate, String toDate, 
            Integer offset, Integer limit) {
        log.debug("Retrieving audits from {} to {}", fromDate, toDate);
        
        AuditResponse response = auditClient.getAuditsByDateRange(fromDate, toDate, offset, limit);
        return ApiResponse.success(response);
    }
    
    /**
     * List available reports.
     * 
     * @return API response with list of reports
     */
    public ApiResponse<List<Object>> listReports() {
        log.debug("Listing available reports");
        
        List<Object> reports = auditClient.listReports();
        return ApiResponse.success(reports);
    }
    
    /**
     * Run a specific report.
     * 
     * @param reportName Name of the report to run
     * @param parameters Optional report parameters
     * @return API response with report results
     */
    public ApiResponse<Object> runReport(String reportName, Map<String, Object> parameters) {
        log.info("Running report: {}", reportName);
        
        Object response = auditClient.runReport(reportName, parameters);
        return ApiResponse.success("Report generated successfully", response);
    }
    
    /**
     * Create a custom report template.
     * 
     * @param reportDefinition Report template definition
     * @return API response with created report
     */
    public ApiResponse<Object> createReport(Object reportDefinition) {
        log.info("Creating custom report");
        
        Object response = auditClient.createReport(reportDefinition);
        return ApiResponse.success("Report template created successfully", response);
    }
}
