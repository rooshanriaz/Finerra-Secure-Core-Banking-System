package com.fyp.cbc.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fyp.cbc.client.LoansClient;
import com.fyp.cbc.dto.request.CreateLoanRequest;
import com.fyp.cbc.dto.request.RescheduleRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.LoanResponse;
import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.model.LoanReviewWorkflow;
import com.fyp.cbc.model.LoanWorkflowStatus;
import com.fyp.cbc.repository.LoanReviewWorkflowRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for loan management operations.
 * Handles loan applications, approvals, disbursements, and rescheduling.
 */
@Service
@RequiredArgsConstructor
public class LoanService {
    
    private static final Logger log = LoggerFactory.getLogger(LoanService.class);
    
    private final LoansClient loansClient;
    private final LoanReviewWorkflowRepository workflowRepository;
    
    /**
     * Create a new loan application.
     * 
     * @param request Loan creation details
     * @return API response containing created loan
     */
    public ApiResponse<LoanResponse> createLoan(CreateLoanRequest request) {
        log.info("Creating loan application for client: {}", request.getClientId());
        
        LoanResponse response = loansClient.createLoan(request);
        
        log.info("Loan application created with ID: {}", response.getLoanId());
        return ApiResponse.success("Loan application created successfully", response);
    }
    
    /**
     * Retrieve loan details by ID.
     * 
     * @param loanId The loan ID
     * @return API response containing loan details
     */
    public ApiResponse<LoanResponse> getLoan(Long loanId) {
        log.debug("Retrieving loan: {}", loanId);
        
        LoanResponse response = loansClient.getLoan(loanId);
        enrichWorkflow(response);
        return ApiResponse.success(response);
    }
    
    /**
     * Update loan details.
     * 
     * @param loanId The loan ID
     * @param request Updated loan details
     * @return API response containing updated loan
     */
    public ApiResponse<LoanResponse> updateLoan(Long loanId, CreateLoanRequest request) {
        log.info("Updating loan: {}", loanId);
        
        LoanResponse response = loansClient.updateLoan(loanId, request);
        
        log.info("Loan {} updated successfully", loanId);
        return ApiResponse.success("Loan updated successfully", response);
    }
    
    /**
     * Approve a loan application.
     * 
     * @param loanId The loan ID
     * @param approvedOnDate Date of approval (format: dd MMMM yyyy)
     * @param approvedLoanAmount Approved amount (optional)
     * @param note Approval note (optional)
     * @return API response with approval result
     */
    public ApiResponse<Object> approveLoan(Long loanId, String approvedOnDate, 
            BigDecimal approvedLoanAmount, String note) {
        requireAnyRole("ROLE_MANAGER", "ROLE_ADMIN");
        if (hasRole("ROLE_MANAGER")) {
            requireForwarded(loanId, "Loan must be forwarded to Branch Manager before final approval.");
        }

        log.info("Approving loan: {} with amount: {}", loanId, approvedLoanAmount);
        
        Object response = loansClient.approveLoan(loanId, approvedOnDate, approvedLoanAmount, note);
        upsertManagerDecision(loanId, LoanWorkflowStatus.APPROVED_BY_MANAGER, note);
        
        log.info("Loan {} approved successfully", loanId);
        return ApiResponse.success("Loan approved successfully", response);
    }

    /**
     * Two-step review step 1:
     * Loan Officer forwards to Branch Manager without approving in Fineract.
     */
    public ApiResponse<Object> forwardToBranchManager(Long loanId, String note) {
        requireAnyRole("ROLE_LOAN_OFFICER", "ROLE_ADMIN");

        LoanResponse loan = loansClient.getLoan(loanId);
        if (loan == null || loan.getStatus() == null || !Boolean.TRUE.equals(loan.getStatus().getPendingApproval())) {
            throw new FineractApiException(
                "Loan can only be forwarded while pending approval.",
                409,
                "INVALID_WORKFLOW_STATE");
        }

        LoanReviewWorkflow workflow = workflowRepository.findByLoanId(loanId)
            .orElseGet(() -> {
                LoanReviewWorkflow w = new LoanReviewWorkflow();
                w.setLoanId(loanId);
                return w;
            });

        String actor = currentUsername() != null ? currentUsername() : "system";
        workflow.setStatus(LoanWorkflowStatus.FORWARDED_TO_MANAGER);
        workflow.setForwardedBy(actor);
        workflow.setForwardedAt(LocalDateTime.now());
        workflow.setNote(note);
        workflowRepository.save(workflow);

        log.info("Loan {} forwarded to Branch Manager by {}", loanId, actor);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("loanId", loanId);
        payload.put("workflowStatus", LoanWorkflowStatus.FORWARDED_TO_MANAGER.name());
        payload.put("forwardedBy", workflow.getForwardedBy());
        payload.put("forwardedAt", String.valueOf(workflow.getForwardedAt()));
        payload.put("note", workflow.getNote());
        return ApiResponse.success("Loan forwarded to Branch Manager", payload);
    }
    
    /**
     * Disburse an approved loan.
     * 
     * @param loanId The loan ID
     * @param actualDisbursementDate Date of disbursement (format: dd MMMM yyyy)
     * @param transactionAmount Disbursement amount (optional)
     * @return API response with disbursement result
     */
    public ApiResponse<Object> disburseLoan(Long loanId, String actualDisbursementDate, 
            BigDecimal transactionAmount) {
        requireAnyRole("ROLE_MANAGER", "ROLE_ADMIN");
        log.info("Disbursing loan: {} with amount: {}", loanId, transactionAmount);
        
        Object response = loansClient.disburseLoan(loanId, actualDisbursementDate, transactionAmount);
        
        log.info("Loan {} disbursed successfully", loanId);
        return ApiResponse.success("Loan disbursed successfully", response);
    }
    
    /**
     * Reject a loan application.
     * 
     * @param loanId The loan ID
     * @param rejectedOnDate Date of rejection (format: dd MMMM yyyy)
     * @param note Rejection reason
     * @return API response with rejection result
     */
    public ApiResponse<Object> rejectLoan(Long loanId, String rejectedOnDate, String note) {
        requireAnyRole("ROLE_LOAN_OFFICER", "ROLE_MANAGER", "ROLE_ADMIN");

        if (hasRole("ROLE_MANAGER")) {
            requireForwarded(loanId, "Loan must be forwarded to Branch Manager before manager rejection.");
        }

        log.info("Rejecting loan: {} with reason: {}", loanId, note);
        
        Object response = loansClient.rejectLoan(loanId, rejectedOnDate, note);

        LoanWorkflowStatus status = hasRole("ROLE_MANAGER") || hasRole("ROLE_ADMIN")
            ? LoanWorkflowStatus.REJECTED_BY_MANAGER
            : LoanWorkflowStatus.REJECTED_BY_LOAN_OFFICER;
        upsertManagerDecision(loanId, status, note);
        
        log.info("Loan {} rejected", loanId);
        return ApiResponse.success("Loan application rejected", response);
    }
    
    /**
     * Search loans.
     * 
     * @param searchQuery Optional search query
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return API response containing list of loans
     */
    public ApiResponse<List<LoanResponse>> searchLoans(String searchQuery, Integer offset, Integer limit) {
        log.debug("Searching loans: query={}", searchQuery);
        
        List<LoanResponse> loans = loansClient.searchLoans(searchQuery, offset, limit);
        loans.forEach(this::enrichWorkflow);
        return ApiResponse.success(loans);
    }
    
    /**
     * Get loan transactions.
     * 
     * @param loanId The loan ID
     * @return API response with list of transactions
     */
    public ApiResponse<List<Object>> getLoanTransactions(Long loanId) {
        log.debug("Retrieving transactions for loan: {}", loanId);
        
        List<Object> transactions = loansClient.getLoanTransactions(loanId);
        return ApiResponse.success(transactions);
    }

    /**
     * Get available loan products from Fineract.
     *
     * @return API response with loan products
     */
    public ApiResponse<List<Object>> getLoanProducts() {
        log.debug("Retrieving loan products");

        List<Object> products = loansClient.getLoanProducts();
        return ApiResponse.success(products);
    }

    /**
     * Create a loan product in Fineract.
     *
     * @param request Loan product payload
     * @return API response with created product
     */
    public ApiResponse<Object> createLoanProduct(Map<String, Object> request) {
        log.info("Creating loan product");

        Map<String, Object> payload = normalizeLoanProductPayload(request);
        Object response = loansClient.createLoanProduct(payload);
        return ApiResponse.success("Loan product created successfully", response);
    }
    
    // ============ Reschedule Loans ============
    
    /**
     * Get all reschedule requests.
     * 
     * @return API response with list of reschedule requests
     */
    public ApiResponse<List<Object>> getRescheduleRequests() {
        log.debug("Retrieving all reschedule requests");
        
        List<Object> requests = loansClient.getRescheduleRequests();
        return ApiResponse.success(requests);
    }
    
    /**
     * Create a reschedule request.
     * 
     * @param request Reschedule request details
     * @return API response with created request
     */
    public ApiResponse<Object> createRescheduleRequest(RescheduleRequest request) {
        log.info("Creating reschedule request for loan: {}", request.getLoanId());
        
        Object response = loansClient.createRescheduleRequest(request);
        
        log.info("Reschedule request created for loan: {}", request.getLoanId());
        return ApiResponse.success("Reschedule request created successfully", response);
    }
    
    /**
     * Get reschedule request by ID.
     * 
     * @param scheduleId The reschedule request ID
     * @return API response with request details
     */
    public ApiResponse<Object> getRescheduleRequest(Long scheduleId) {
        log.debug("Retrieving reschedule request: {}", scheduleId);
        
        Object response = loansClient.getRescheduleRequest(scheduleId);
        return ApiResponse.success(response);
    }

    private void enrichWorkflow(LoanResponse loan) {
        if (loan == null) return;
        Long loanId = extractLoanId(loan);
        if (loanId == null) return;

        workflowRepository.findByLoanId(loanId).ifPresent(wf -> {
            loan.setWorkflowStatus(wf.getStatus().name());
            loan.setForwardedToManager(
                wf.getStatus() == LoanWorkflowStatus.FORWARDED_TO_MANAGER
                    || wf.getStatus() == LoanWorkflowStatus.APPROVED_BY_MANAGER
                    || wf.getStatus() == LoanWorkflowStatus.REJECTED_BY_MANAGER
            );
            loan.setForwardedBy(wf.getForwardedBy());
            loan.setManagerDecisionBy(wf.getManagerDecisionBy());
            loan.setWorkflowNote(wf.getNote());
        });
    }

    private Long extractLoanId(LoanResponse loan) {
        if (loan.getId() != null) return loan.getId();
        if (loan.getLoanId() != null) return loan.getLoanId();
        return loan.getResourceId();
    }

    private void requireForwarded(Long loanId, String message) {
        boolean forwarded = workflowRepository.findByLoanId(loanId)
            .map(w -> w.getStatus() == LoanWorkflowStatus.FORWARDED_TO_MANAGER)
            .orElse(false);
        if (!forwarded) {
            throw new FineractApiException(message, 409, "INVALID_WORKFLOW_STATE");
        }
    }

    private void upsertManagerDecision(Long loanId, LoanWorkflowStatus status, String note) {
        LoanReviewWorkflow workflow = workflowRepository.findByLoanId(loanId)
            .orElseGet(() -> {
                LoanReviewWorkflow w = new LoanReviewWorkflow();
                w.setLoanId(loanId);
                return w;
            });

        if (status == LoanWorkflowStatus.FORWARDED_TO_MANAGER) {
            workflow.setForwardedBy(currentUsername());
            workflow.setForwardedAt(LocalDateTime.now());
        } else {
            workflow.setManagerDecisionBy(currentUsername());
            workflow.setManagerDecisionAt(LocalDateTime.now());
        }
        workflow.setStatus(status);
        workflow.setNote(note);
        workflowRepository.save(workflow);
    }

    private void requireAnyRole(String... roles) {
        Set<String> current = currentRoles();
        for (String role : roles) {
            if (current.contains(role)) return;
        }
        throw new FineractApiException(
            "You are not authorized for this workflow step.",
            403,
            "ACCESS_DENIED");
    }

    private boolean hasRole(String role) {
        return currentRoles().contains(role);
    }

    private Set<String> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return Set.of();
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toSet());
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }

    /**
     * Normalize/augment loan-product payload for Fineract compatibility.
     * Different Fineract builds require slightly different "mandatory" fields.
     * We set conservative defaults while preserving caller-provided values.
     */
    private Map<String, Object> normalizeLoanProductPayload(Map<String, Object> raw) {
        Map<String, Object> payload = new HashMap<>();
        if (raw != null) payload.putAll(raw);

        // Core identity
        payload.putIfAbsent("name", "Personal Loan");
        payload.putIfAbsent("shortName", "PL");
        payload.putIfAbsent("description", "Loan product created from Finnera");

        // Currency + amount configuration
        payload.putIfAbsent("currencyCode", "PKR");
        payload.putIfAbsent("digitsAfterDecimal", 2);
        payload.putIfAbsent("inMultiplesOf", 0);
        payload.putIfAbsent("principal", toNumber(payload.get("principal"), 10000));
        payload.putIfAbsent("minPrincipal", toNumber(payload.get("minPrincipal"), toNumber(payload.get("principal"), 10000)));
        payload.putIfAbsent("maxPrincipal", toNumber(payload.get("maxPrincipal"), toNumber(payload.get("principal"), 10000)));

        // Term and repayment
        payload.putIfAbsent("numberOfRepayments", toInt(payload.get("numberOfRepayments"), 12));
        payload.putIfAbsent("minNumberOfRepayments", toInt(payload.get("minNumberOfRepayments"), toInt(payload.get("numberOfRepayments"), 12)));
        payload.putIfAbsent("maxNumberOfRepayments", toInt(payload.get("maxNumberOfRepayments"), toInt(payload.get("numberOfRepayments"), 12)));
        payload.putIfAbsent("repaymentEvery", toInt(payload.get("repaymentEvery"), 1));
        payload.putIfAbsent("repaymentFrequencyType", toInt(payload.get("repaymentFrequencyType"), 2)); // monthly

        // Interest & computation
        payload.putIfAbsent("interestRatePerPeriod", toNumber(payload.get("interestRatePerPeriod"), 15));
        payload.putIfAbsent("minInterestRatePerPeriod", toNumber(payload.get("minInterestRatePerPeriod"), toNumber(payload.get("interestRatePerPeriod"), 15)));
        payload.putIfAbsent("maxInterestRatePerPeriod", toNumber(payload.get("maxInterestRatePerPeriod"), toNumber(payload.get("interestRatePerPeriod"), 15)));
        payload.putIfAbsent("interestRateFrequencyType", toInt(payload.get("interestRateFrequencyType"), 2)); // monthly
        payload.putIfAbsent("interestType", toInt(payload.get("interestType"), 1)); // declining balance
        payload.putIfAbsent("interestCalculationPeriodType", toInt(payload.get("interestCalculationPeriodType"), 1));
        payload.putIfAbsent("amortizationType", toInt(payload.get("amortizationType"), 1));

        // Processing/accounting
        payload.putIfAbsent("transactionProcessingStrategyCode",
            payload.getOrDefault("transactionProcessingStrategyCode", "mifos-standard-strategy"));
        // Some Fineract variants reject strategy ID and accept only strategy CODE.
        payload.remove("transactionProcessingStrategyId");
        payload.putIfAbsent("accountingRule", toInt(payload.get("accountingRule"), 1));

        // Version-specific mandatory flags observed in your Fineract build
        payload.putIfAbsent("daysInYearType", toInt(payload.get("daysInYearType"), 1));
        payload.putIfAbsent("daysInMonthType", toInt(payload.get("daysInMonthType"), 1));
        payload.putIfAbsent("isInterestRecalculationEnabled",
            payload.containsKey("isInterestRecalculationEnabled")
                ? payload.get("isInterestRecalculationEnabled")
                : Boolean.FALSE);

        // Locale/date defaults
        payload.putIfAbsent("locale", "en");
        payload.putIfAbsent("dateFormat", "dd MMMM yyyy");

        // Clean unsupported field for your current Fineract variant
        if (payload.containsKey("annualInterestRate")) {
            payload.remove("annualInterestRate");
        }

        return payload;
    }

    private int toInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double toNumber(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
