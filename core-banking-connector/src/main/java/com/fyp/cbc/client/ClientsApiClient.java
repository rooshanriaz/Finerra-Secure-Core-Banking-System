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

import com.fyp.cbc.dto.request.ClientIdentifierRequest;
import com.fyp.cbc.dto.request.CreateClientRequest;
import com.fyp.cbc.dto.response.ClientResponse;
import com.fyp.cbc.dto.response.PagedResponse;
import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.exception.ResourceNotFoundException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import reactor.core.publisher.Mono;

/**
 * Client for Fineract Clients APIs.
 * Handles customer onboarding and KYC-related operations.
 */
@Component
public class ClientsApiClient {
    
    private static final Logger log = LoggerFactory.getLogger(ClientsApiClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "fineractClient";
    
    private final WebClient fineractWebClient;
    private final AuthenticationClient authClient;
    
    public ClientsApiClient(
            @Qualifier("fineractWebClient") WebClient fineractWebClient,
            AuthenticationClient authClient) {
        this.fineractWebClient = fineractWebClient;
        this.authClient = authClient;
    }
    
    /**
     * Create a new client (customer onboarding).
     * POST /v1/clients
     * 
     * @param request Client creation details
     * @return Created client response with ID
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "createClientFallback")
    @Retry(name = "fineractApi")
    public ClientResponse createClient(CreateClientRequest request) {
        log.debug("Creating client: {} {}", request.getFirstname(), request.getLastname());
        
        return fineractWebClient.post()
            .uri("/v1/clients")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Failed to create client: {}", body);
                        return Mono.error(new FineractApiException(
                            "Failed to create client: " + body,
                            response.statusCode().value()));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while creating client",
                    response.statusCode().value())))
            .bodyToMono(ClientResponse.class)
            .block();
    }
    
    /**
     * Retrieve client details by ID.
     * GET /v1/clients/{clientId}
     * 
     * @param clientId The client ID
     * @return Client details
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getClientFallback")
    @Retry(name = "fineractApi")
    public ClientResponse getClient(Long clientId) {
        log.debug("Retrieving client: {}", clientId);
        
        return fineractWebClient.get()
            .uri("/v1/clients/{clientId}", clientId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Client not found: {}", clientId);
                return Mono.error(new ResourceNotFoundException("Client", clientId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve client",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving client",
                    response.statusCode().value())))
            .bodyToMono(ClientResponse.class)
            .block();
    }
    
    /**
     * Update client information.
     * PUT /v1/clients/{clientId}
     * 
     * @param clientId The client ID
     * @param request Updated client details
     * @return Updated client response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "updateClientFallback")
    @Retry(name = "fineractApi")
    public ClientResponse updateClient(Long clientId, CreateClientRequest request) {
        log.debug("Updating client: {}", clientId);
        
        return fineractWebClient.put()
            .uri("/v1/clients/{clientId}", clientId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Client not found for update: {}", clientId);
                return Mono.error(new ResourceNotFoundException("Client", clientId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to update client: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while updating client",
                    response.statusCode().value())))
            .bodyToMono(ClientResponse.class)
            .block();
    }
    
    /**
     * Search clients.
     * GET /v1/clients
     * 
     * Fineract returns a paginated response:
     * {"totalFilteredRecords": N, "pageItems": [...]}
     * 
     * @param searchQuery Optional search query
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return List of clients matching criteria
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "searchClientsFallback")
    @Retry(name = "fineractApi")
    public List<ClientResponse> searchClients(String searchQuery, Integer offset, Integer limit) {
        log.debug("Searching clients: query={}, offset={}, limit={}", searchQuery, offset, limit);
        
        PagedResponse<ClientResponse> response = fineractWebClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/v1/clients");
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
            .onStatus(HttpStatusCode::is4xxClientError, resp ->
                Mono.error(new FineractApiException(
                    "Failed to search clients",
                    resp.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, resp ->
                Mono.error(new FineractApiException(
                    "Fineract server error while searching clients",
                    resp.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<PagedResponse<ClientResponse>>() {})
            .block();
        
        return response != null && response.getPageItems() != null 
            ? response.getPageItems() 
            : List.of();
    }
    
    /**
     * Add client identifier (e.g., CNIC for KYC).
     * POST /v1/clients/{clientId}/identifiers
     * 
     * @param clientId The client ID
     * @param request Identifier details
     * @return Response with identifier ID
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "addClientIdentifierFallback")
    @Retry(name = "fineractApi")
    public Object addClientIdentifier(Long clientId, ClientIdentifierRequest request) {
        log.debug("Adding identifier for client: {}", clientId);
        
        return fineractWebClient.post()
            .uri("/v1/clients/{clientId}/identifiers", clientId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Client not found for identifier: {}", clientId);
                return Mono.error(new ResourceNotFoundException("Client", clientId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to add client identifier: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while adding identifier",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * Get client identifiers.
     * GET /v1/clients/{clientId}/identifiers
     * 
     * @param clientId The client ID
     * @return List of client identifiers
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getClientIdentifiersFallback")
    @Retry(name = "fineractApi")
    public List<Object> getClientIdentifiers(Long clientId) {
        log.debug("Retrieving identifiers for client: {}", clientId);
        
        return fineractWebClient.get()
            .uri("/v1/clients/{clientId}/identifiers", clientId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Client not found: {}", clientId);
                return Mono.error(new ResourceNotFoundException("Client", clientId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve client identifiers",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving identifiers",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<List<Object>>() {})
            .block();
    }
    
    /**
     * Upload client image for identity verification.
     * POST /v1/clients/{clientId}/images
     * 
     * @param clientId The client ID
     * @param imageBase64 Base64 encoded image
     * @return Upload result
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "uploadClientImageFallback")
    @Retry(name = "fineractApi")
    public Object uploadClientImage(Long clientId, String imageBase64) {
        log.debug("Uploading image for client: {}", clientId);
        
        return fineractWebClient.post()
            .uri("/v1/clients/{clientId}/images", clientId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(imageBase64)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Client not found for image upload: {}", clientId);
                return Mono.error(new ResourceNotFoundException("Client", clientId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to upload client image: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while uploading image",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    // Fallback methods
    
    private ClientResponse createClientFallback(CreateClientRequest request, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "createClient");
    }

    private ClientResponse getClientFallback(Long clientId, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "getClient");
    }

    private ClientResponse updateClientFallback(Long clientId, CreateClientRequest request, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "updateClient");
    }

    private List<ClientResponse> searchClientsFallback(String searchQuery, Integer offset, Integer limit, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "searchClients");
    }

    private Object addClientIdentifierFallback(Long clientId, ClientIdentifierRequest request, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "addClientIdentifier");
    }

    private List<Object> getClientIdentifiersFallback(Long clientId, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "getClientIdentifiers");
    }

    private Object uploadClientImageFallback(Long clientId, String imageBase64, Throwable t) {
        throw CircuitBreakerFallbacks.rethrowPreservingFineract(t, "uploadClientImage");
    }
}
