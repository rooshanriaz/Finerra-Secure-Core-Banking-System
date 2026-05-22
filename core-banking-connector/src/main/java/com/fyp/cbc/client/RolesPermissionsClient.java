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

import com.fyp.cbc.dto.request.CreateRoleRequest;
import com.fyp.cbc.dto.response.RoleResponse;
import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.exception.ResourceNotFoundException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import reactor.core.publisher.Mono;

/**
 * Client for Fineract Roles and Permissions APIs.
 * Handles RBAC and insider threat mitigation through permission management.
 */
@Component
public class RolesPermissionsClient {
    
    private static final Logger log = LoggerFactory.getLogger(RolesPermissionsClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "fineractAuth";
    
    private final WebClient fineractWebClient;
    private final AuthenticationClient authClient;
    
    public RolesPermissionsClient(
            @Qualifier("fineractWebClient") WebClient fineractWebClient,
            AuthenticationClient authClient) {
        this.fineractWebClient = fineractWebClient;
        this.authClient = authClient;
    }
    
    /**
     * Create a new role.
     * POST /v1/roles
     * 
     * @param request Role creation details
     * @return Created role response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "createRoleFallback")
    @Retry(name = "fineractApi")
    public RoleResponse createRole(CreateRoleRequest request) {
        log.debug("Creating role: {}", request.getName());
        
        return fineractWebClient.post()
            .uri("/v1/roles")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Failed to create role: {}", body);
                        return Mono.error(new FineractApiException(
                            "Failed to create role: " + body,
                            response.statusCode().value()));
                    }))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while creating role",
                    response.statusCode().value())))
            .bodyToMono(RoleResponse.class)
            .block();
    }
    
    /**
     * List all roles.
     * GET /v1/roles
     * 
     * @return List of all roles
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "listRolesFallback")
    @Retry(name = "fineractApi")
    public List<RoleResponse> listRoles() {
        log.debug("Listing all roles");
        
        return fineractWebClient.get()
            .uri("/v1/roles")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to list roles",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while listing roles",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<List<RoleResponse>>() {})
            .block();
    }
    
    /**
     * Get role details by ID.
     * GET /v1/roles/{roleId}
     * 
     * @param roleId The role ID
     * @return Role details
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getRoleFallback")
    @Retry(name = "fineractApi")
    public RoleResponse getRole(Long roleId) {
        log.debug("Retrieving role: {}", roleId);
        
        return fineractWebClient.get()
            .uri("/v1/roles/{roleId}", roleId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Role not found: {}", roleId);
                return Mono.error(new ResourceNotFoundException("Role", roleId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve role",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving role",
                    response.statusCode().value())))
            .bodyToMono(RoleResponse.class)
            .block();
    }
    
    /**
     * Update a role.
     * PUT /v1/roles/{roleId}
     * 
     * @param roleId The role ID
     * @param request Updated role details
     * @return Updated role response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "updateRoleFallback")
    @Retry(name = "fineractApi")
    public RoleResponse updateRole(Long roleId, CreateRoleRequest request) {
        log.debug("Updating role: {}", roleId);
        
        return fineractWebClient.put()
            .uri("/v1/roles/{roleId}", roleId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Role not found for update: {}", roleId);
                return Mono.error(new ResourceNotFoundException("Role", roleId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to update role: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while updating role",
                    response.statusCode().value())))
            .bodyToMono(RoleResponse.class)
            .block();
    }
    
    /**
     * Get permissions for a role.
     * GET /v1/roles/{roleId}/permissions
     * 
     * @param roleId The role ID
     * @return List of permissions for the role
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getRolePermissionsFallback")
    @Retry(name = "fineractApi")
    public RoleResponse.PermissionsData getRolePermissions(Long roleId) {
        log.debug("Retrieving permissions for role: {}", roleId);
        
        return fineractWebClient.get()
            .uri("/v1/roles/{roleId}/permissions", roleId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Role not found: {}", roleId);
                return Mono.error(new ResourceNotFoundException("Role", roleId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to retrieve role permissions",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while retrieving role permissions",
                    response.statusCode().value())))
            .bodyToMono(RoleResponse.PermissionsData.class)
            .block();
    }
    
    /**
     * Update permissions for a role.
     * PUT /v1/roles/{roleId}/permissions
     * 
     * @param roleId The role ID
     * @param permissions Permissions update request
     * @return Updated permissions response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "updateRolePermissionsFallback")
    @Retry(name = "fineractApi")
    public Object updateRolePermissions(Long roleId, Object permissions) {
        log.debug("Updating permissions for role: {}", roleId);
        
        return fineractWebClient.put()
            .uri("/v1/roles/{roleId}/permissions", roleId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(permissions)
            .retrieve()
            .onStatus(status -> status.value() == 404, response -> {
                log.warn("Role not found for permission update: {}", roleId);
                return Mono.error(new ResourceNotFoundException("Role", roleId));
            })
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new FineractApiException(
                        "Failed to update role permissions: " + body,
                        response.statusCode().value()))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while updating role permissions",
                    response.statusCode().value())))
            .bodyToMono(Object.class)
            .block();
    }
    
    /**
     * List all available permissions.
     * GET /v1/permissions
     * 
     * @return List of all available permissions
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "listPermissionsFallback")
    @Retry(name = "fineractApi")
    public List<RoleResponse.PermissionData> listPermissions() {
        log.debug("Listing all permissions");
        
        return fineractWebClient.get()
            .uri("/v1/permissions")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authClient.getDefaultAuthKey())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                Mono.error(new FineractApiException(
                    "Failed to list permissions",
                    response.statusCode().value())))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error while listing permissions",
                    response.statusCode().value())))
            .bodyToMono(new ParameterizedTypeReference<List<RoleResponse.PermissionData>>() {})
            .block();
    }
    
    // Fallback methods
    
    private RoleResponse createRoleFallback(CreateRoleRequest request, Throwable t) {
        log.error("Circuit breaker fallback for createRole: {}", t.getMessage());
        throw new FineractApiException(
            "Role service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private List<RoleResponse> listRolesFallback(Throwable t) {
        log.error("Circuit breaker fallback for listRoles: {}", t.getMessage());
        throw new FineractApiException(
            "Role service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private RoleResponse getRoleFallback(Long roleId, Throwable t) {
        log.error("Circuit breaker fallback for getRole: {}", t.getMessage());
        throw new FineractApiException(
            "Role service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private RoleResponse updateRoleFallback(Long roleId, CreateRoleRequest request, Throwable t) {
        log.error("Circuit breaker fallback for updateRole: {}", t.getMessage());
        throw new FineractApiException(
            "Role service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private RoleResponse.PermissionsData getRolePermissionsFallback(Long roleId, Throwable t) {
        log.error("Circuit breaker fallback for getRolePermissions: {}", t.getMessage());
        throw new FineractApiException(
            "Role service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private Object updateRolePermissionsFallback(Long roleId, Object permissions, Throwable t) {
        log.error("Circuit breaker fallback for updateRolePermissions: {}", t.getMessage());
        throw new FineractApiException(
            "Role service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private List<RoleResponse.PermissionData> listPermissionsFallback(Throwable t) {
        log.error("Circuit breaker fallback for listPermissions: {}", t.getMessage());
        throw new FineractApiException(
            "Permission service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
}
