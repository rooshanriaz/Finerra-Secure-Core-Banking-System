package com.fyp.cbc.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fyp.cbc.client.TransactionClient;
import com.fyp.cbc.dto.request.TransactionRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.TransactionResponse;
import com.fyp.cbc.dto.response.TransactionResponse.JournalEntryResponse;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for transaction operations.
 * Handles deposits, withdrawals, loan repayments, and journal entries.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {
    
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    
    private final TransactionClient transactionClient;
    
    /**
     * Deposit funds to a savings account.
     * 
     * @param accountId The savings account ID
     * @param amount Amount to deposit
     * @param transactionDate Date of transaction (format: dd MMMM yyyy)
     * @param note Optional note
     * @return API response with transaction result
     */
    public ApiResponse<TransactionResponse> deposit(Long accountId, BigDecimal amount, 
            String transactionDate, String note) {
        log.info("Processing deposit of {} to account: {}", amount, accountId);
        
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(amount)
            .transactionDate(transactionDate)
            .note(note)
            .build();
        
        TransactionResponse response = transactionClient.deposit(accountId, request);
        
        log.info("Deposit of {} to account {} completed. Transaction ID: {}", 
            amount, accountId, response.getResourceId());
        return ApiResponse.success("Deposit completed successfully", response);
    }
    
    /**
     * Deposit funds using a request object.
     * 
     * @param accountId The savings account ID
     * @param request Transaction details
     * @return API response with transaction result
     */
    public ApiResponse<TransactionResponse> deposit(Long accountId, TransactionRequest request) {
        log.info("Processing deposit of {} to account: {}", request.getTransactionAmount(), accountId);
        
        TransactionResponse response = transactionClient.deposit(accountId, request);
        
        log.info("Deposit completed. Transaction ID: {}", response.getResourceId());
        return ApiResponse.success("Deposit completed successfully", response);
    }
    
    /**
     * Withdraw funds from a savings account.
     * 
     * @param accountId The savings account ID
     * @param amount Amount to withdraw
     * @param transactionDate Date of transaction (format: dd MMMM yyyy)
     * @param note Optional note
     * @return API response with transaction result
     */
    public ApiResponse<TransactionResponse> withdraw(Long accountId, BigDecimal amount, 
            String transactionDate, String note) {
        log.info("Processing withdrawal of {} from account: {}", amount, accountId);
        
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(amount)
            .transactionDate(transactionDate)
            .note(note)
            .build();
        
        TransactionResponse response = transactionClient.withdraw(accountId, request);
        
        log.info("Withdrawal of {} from account {} completed. Transaction ID: {}", 
            amount, accountId, response.getResourceId());
        return ApiResponse.success("Withdrawal completed successfully", response);
    }
    
    /**
     * Withdraw funds using a request object.
     * 
     * @param accountId The savings account ID
     * @param request Transaction details
     * @return API response with transaction result
     */
    public ApiResponse<TransactionResponse> withdraw(Long accountId, TransactionRequest request) {
        log.info("Processing withdrawal of {} from account: {}", request.getTransactionAmount(), accountId);
        
        TransactionResponse response = transactionClient.withdraw(accountId, request);
        
        log.info("Withdrawal completed. Transaction ID: {}", response.getResourceId());
        return ApiResponse.success("Withdrawal completed successfully", response);
    }
    
    /**
     * Make a loan repayment.
     * 
     * @param loanId The loan ID
     * @param amount Repayment amount
     * @param transactionDate Date of transaction (format: dd MMMM yyyy)
     * @param note Optional note
     * @return API response with transaction result
     */
    public ApiResponse<TransactionResponse> loanRepayment(Long loanId, BigDecimal amount, 
            String transactionDate, String note) {
        log.info("Processing loan repayment of {} for loan: {}", amount, loanId);
        
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(amount)
            .transactionDate(transactionDate)
            .note(note)
            .build();
        
        TransactionResponse response = transactionClient.loanRepayment(loanId, request);
        
        log.info("Loan repayment of {} for loan {} completed. Transaction ID: {}", 
            amount, loanId, response.getResourceId());
        return ApiResponse.success("Loan repayment completed successfully", response);
    }
    
    /**
     * Make a loan repayment using a request object.
     * 
     * @param loanId The loan ID
     * @param request Transaction details
     * @return API response with transaction result
     */
    public ApiResponse<TransactionResponse> loanRepayment(Long loanId, TransactionRequest request) {
        log.info("Processing loan repayment of {} for loan: {}", request.getTransactionAmount(), loanId);
        
        TransactionResponse response = transactionClient.loanRepayment(loanId, request);
        
        log.info("Loan repayment completed. Transaction ID: {}", response.getResourceId());
        return ApiResponse.success("Loan repayment completed successfully", response);
    }
    
    /**
     * Retrieve journal entries.
     * 
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @param officeId Optional office filter
     * @param glAccountId Optional GL account filter
     * @param manualEntriesOnly Filter for manual entries only
     * @return API response with journal entries
     */
    public ApiResponse<JournalEntryResponse> getJournalEntries(Integer offset, Integer limit, 
            Long officeId, Long glAccountId, Boolean manualEntriesOnly) {
        log.debug("Retrieving journal entries: offset={}, limit={}", offset, limit);
        
        JournalEntryResponse response = transactionClient.getJournalEntries(
            offset, limit, officeId, glAccountId, manualEntriesOnly);
        return ApiResponse.success(response);
    }
    
    /**
     * Get specific transaction details by transaction ID.
     * 
     * @param transactionId The transaction ID
     * @return API response with transaction details
     */
    public ApiResponse<JournalEntryResponse> getTransactionDetails(String transactionId) {
        log.debug("Retrieving transaction details: {}", transactionId);
        
        JournalEntryResponse response = transactionClient.getTransactionDetails(transactionId);
        return ApiResponse.success(response);
    }
}
