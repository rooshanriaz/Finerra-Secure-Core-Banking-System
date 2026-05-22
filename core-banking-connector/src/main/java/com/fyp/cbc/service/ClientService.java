package com.fyp.cbc.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fyp.cbc.client.ClientsApiClient;
import com.fyp.cbc.dto.request.ClientIdentifierRequest;
import com.fyp.cbc.dto.request.CreateClientRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.ClientResponse;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for client/customer management operations.
 * Handles customer onboarding operations.
 */
@Service
@RequiredArgsConstructor
public class ClientService {
    
    private static final Logger log = LoggerFactory.getLogger(ClientService.class);
    
    private final ClientsApiClient clientsApiClient;
    
    /**
     * Create a new client (customer onboarding).
     * 
     * @param request Client creation details
     * @return API response containing created client
     */
    public ApiResponse<ClientResponse> createClient(CreateClientRequest request) {
        log.info("Creating new client: {} {}", request.getFirstname(), request.getLastname());
        
        ClientResponse response = clientsApiClient.createClient(request);
        
        log.info("Client created successfully with ID: {}", response.getClientId());
        return ApiResponse.success("Client created successfully", response);
    }
    
    /**
     * Retrieve client details by ID.
     * 
     * @param clientId The client ID
     * @return API response containing client details
     */
    public ApiResponse<ClientResponse> getClient(Long clientId) {
        log.debug("Retrieving client: {}", clientId);
        
        ClientResponse response = clientsApiClient.getClient(clientId);
        return ApiResponse.success(response);
    }
    
    /**
     * Update client information.
     * 
     * @param clientId The client ID
     * @param request Updated client details
     * @return API response containing updated client
     */
    public ApiResponse<ClientResponse> updateClient(Long clientId, CreateClientRequest request) {
        log.info("Updating client: {}", clientId);
        
        ClientResponse response = clientsApiClient.updateClient(clientId, request);
        
        log.info("Client {} updated successfully", clientId);
        return ApiResponse.success("Client updated successfully", response);
    }
    
    /**
     * Search clients based on criteria.
     * 
     * @param searchQuery Optional search query
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return API response containing list of clients
     */
    public ApiResponse<List<ClientResponse>> searchClients(String searchQuery, Integer offset, Integer limit) {
        log.debug("Searching clients: query={}", searchQuery);
        
        List<ClientResponse> clients = clientsApiClient.searchClients(searchQuery, offset, limit);
        return ApiResponse.success(clients);
    }
    
    /**
     * Add client identifier (e.g., CNIC).
     * 
     * @param clientId The client ID
     * @param request Identifier details
     * @return API response with identifier result
     */
    public ApiResponse<Object> addClientIdentifier(Long clientId, ClientIdentifierRequest request) {
        log.info("Adding identifier for client: {}", clientId);
        
        Object response = clientsApiClient.addClientIdentifier(clientId, request);
        return ApiResponse.success("Identifier added successfully", response);
    }
    
    /**
     * Get client identifiers.
     * 
     * @param clientId The client ID
     * @return API response with list of identifiers
     */
    public ApiResponse<List<Object>> getClientIdentifiers(Long clientId) {
        log.debug("Retrieving identifiers for client: {}", clientId);
        
        List<Object> identifiers = clientsApiClient.getClientIdentifiers(clientId);
        return ApiResponse.success(identifiers);
    }
    
    /**
     * Upload client image for identity verification.
     * 
     * @param clientId The client ID
     * @param imageBase64 Base64 encoded image
     * @return API response with upload result
     */
    public ApiResponse<Object> uploadClientImage(Long clientId, String imageBase64) {
        log.info("Uploading image for client: {}", clientId);
        
        Object response = clientsApiClient.uploadClientImage(clientId, imageBase64);
        return ApiResponse.success("Image uploaded successfully", response);
    }
}
