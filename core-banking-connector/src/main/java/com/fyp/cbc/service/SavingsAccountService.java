package com.fyp.cbc.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fyp.cbc.client.SavingsAccountClient;
import com.fyp.cbc.dto.request.CreateSavingsAccountRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.SavingsAccountResponse;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for savings account management operations.
 */
@Service
@RequiredArgsConstructor
public class SavingsAccountService {
    
    private static final Logger log = LoggerFactory.getLogger(SavingsAccountService.class);
    
    private final SavingsAccountClient savingsAccountClient;
    
    /**
     * Create a new savings account for a client.
     * 
     * @param request Savings account creation details
     * @return API response containing created account
     */
    public ApiResponse<SavingsAccountResponse> createSavingsAccount(CreateSavingsAccountRequest request) {
        log.info("Creating savings account for client: {}", request.getClientId());
        
        SavingsAccountResponse response = savingsAccountClient.createSavingsAccount(request);
        
        log.info("Savings account created with ID: {}", response.getSavingsId());
        return ApiResponse.success("Savings account created successfully", response);
    }
    
    /**
     * Retrieve savings account details by ID.
     * 
     * @param accountId The savings account ID
     * @return API response containing account details
     */
    public ApiResponse<SavingsAccountResponse> getSavingsAccount(Long accountId) {
        log.debug("Retrieving savings account: {}", accountId);
        
        SavingsAccountResponse response = savingsAccountClient.getSavingsAccount(accountId);
        return ApiResponse.success(response);
    }
    
    /**
     * Update savings account details.
     * 
     * @param accountId The savings account ID
     * @param request Updated account details
     * @return API response containing updated account
     */
    public ApiResponse<SavingsAccountResponse> updateSavingsAccount(Long accountId, 
            CreateSavingsAccountRequest request) {
        log.info("Updating savings account: {}", accountId);
        
        SavingsAccountResponse response = savingsAccountClient.updateSavingsAccount(accountId, request);
        
        log.info("Savings account {} updated successfully", accountId);
        return ApiResponse.success("Savings account updated successfully", response);
    }
    
    /**
     * Search savings accounts.
     * 
     * @param searchQuery Optional search query
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return API response containing list of accounts
     */
    public ApiResponse<List<SavingsAccountResponse>> searchSavingsAccounts(String searchQuery, 
            Integer offset, Integer limit) {
        log.debug("Searching savings accounts: query={}", searchQuery);
        
        List<SavingsAccountResponse> accounts = savingsAccountClient.searchSavingsAccounts(
            searchQuery, offset, limit);
        return ApiResponse.success(accounts);
    }
    
    /**
     * Approve a savings account.
     * 
     * @param accountId The savings account ID
     * @param approvedOnDate Date of approval (format: dd MMMM yyyy)
     * @return API response with approval result
     */
    public ApiResponse<Object> approveSavingsAccount(Long accountId, String approvedOnDate) {
        log.info("Approving savings account: {}", accountId);
        
        Object response = savingsAccountClient.approveSavingsAccount(accountId, approvedOnDate);
        
        log.info("Savings account {} approved successfully", accountId);
        return ApiResponse.success("Savings account approved successfully", response);
    }
    
    /**
     * Activate a savings account.
     * 
     * @param accountId The savings account ID
     * @param activatedOnDate Date of activation (format: dd MMMM yyyy)
     * @return API response with activation result
     */
    public ApiResponse<Object> activateSavingsAccount(Long accountId, String activatedOnDate) {
        log.info("Activating savings account: {}", accountId);
        
        Object response = savingsAccountClient.activateSavingsAccount(accountId, activatedOnDate);
        
        log.info("Savings account {} activated successfully", accountId);
        return ApiResponse.success("Savings account activated successfully", response);
    }
}
