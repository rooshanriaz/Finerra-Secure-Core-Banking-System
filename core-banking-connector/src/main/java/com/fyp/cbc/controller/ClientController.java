package com.fyp.cbc.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fyp.cbc.dto.request.ClientIdentifierRequest;
import com.fyp.cbc.dto.request.CreateClientRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.ClientResponse;
import com.fyp.cbc.service.ClientService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for client/customer management operations.
 * Provides endpoints for customer onboarding operations.
 */
@RestController
@RequestMapping("/v1/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Customer onboarding APIs")
public class ClientController {
    
    private final ClientService clientService;
    
    /**
     * Create a new client (customer onboarding).
     * POST /v1/clients
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Create client", description = "Create a new client for customer onboarding")
    public ResponseEntity<ApiResponse<ClientResponse>> createClient(
            @Valid @RequestBody CreateClientRequest request) {
        ApiResponse<ClientResponse> response = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Retrieve client details by ID.
     * GET /v1/clients/{clientId}
     */
    @GetMapping("/{clientId}")
    @Operation(summary = "Get client", description = "Retrieve client details by ID")
    public ResponseEntity<ApiResponse<ClientResponse>> getClient(
            @Parameter(description = "Client ID") @PathVariable Long clientId) {
        ApiResponse<ClientResponse> response = clientService.getClient(clientId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update client information.
     * PUT /v1/clients/{clientId}
     */
    @PutMapping("/{clientId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Update client", description = "Update client information")
    public ResponseEntity<ApiResponse<ClientResponse>> updateClient(
            @Parameter(description = "Client ID") @PathVariable Long clientId,
            @Valid @RequestBody CreateClientRequest request) {
        ApiResponse<ClientResponse> response = clientService.updateClient(clientId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Search clients.
     * GET /v1/clients
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search clients", description = "Search clients with optional filters")
    public ResponseEntity<ApiResponse<List<ClientResponse>>> searchClients(
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") Integer offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") Integer limit) {
        ApiResponse<List<ClientResponse>> response = clientService.searchClients(query, offset, limit);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Add client identifier (e.g., CNIC).
     * POST /v1/clients/{clientId}/identifiers
     */
    @PostMapping("/{clientId}/identifiers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Add identifier", description = "Add client identifier (e.g., CNIC)")
    public ResponseEntity<ApiResponse<Object>> addClientIdentifier(
            @Parameter(description = "Client ID") @PathVariable Long clientId,
            @Valid @RequestBody ClientIdentifierRequest request) {
        ApiResponse<Object> response = clientService.addClientIdentifier(clientId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get client identifiers.
     * GET /v1/clients/{clientId}/identifiers
     */
    @GetMapping("/{clientId}/identifiers")
    @Operation(summary = "Get identifiers", description = "Get client identifiers/documents")
    public ResponseEntity<ApiResponse<List<Object>>> getClientIdentifiers(
            @Parameter(description = "Client ID") @PathVariable Long clientId) {
        ApiResponse<List<Object>> response = clientService.getClientIdentifiers(clientId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Upload client image for identity verification.
     * POST /v1/clients/{clientId}/images
     */
    @PostMapping("/{clientId}/images")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @Operation(summary = "Upload image", description = "Upload client image for identity verification")
    public ResponseEntity<ApiResponse<Object>> uploadClientImage(
            @Parameter(description = "Client ID") @PathVariable Long clientId,
            @RequestBody String imageBase64) {
        ApiResponse<Object> response = clientService.uploadClientImage(clientId, imageBase64);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
