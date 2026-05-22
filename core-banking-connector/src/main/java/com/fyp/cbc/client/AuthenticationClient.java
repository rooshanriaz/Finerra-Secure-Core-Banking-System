package com.fyp.cbc.client;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fyp.cbc.config.FineractProperties;
import com.fyp.cbc.dto.request.AuthRequest;
import com.fyp.cbc.dto.response.AuthResponse;
import com.fyp.cbc.exception.AuthenticationFailedException;
import com.fyp.cbc.exception.FineractApiException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import reactor.core.publisher.Mono;

/**
 * Client for Fineract Authentication APIs.
 * Handles user authentication and token generation.
 */
@Component
public class AuthenticationClient {
    
    private static final Logger log = LoggerFactory.getLogger(AuthenticationClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "fineractAuth";
    
    private final WebClient fineractWebClient;
    private final FineractProperties fineractProperties;
    
    public AuthenticationClient(
            @Qualifier("fineractWebClient") WebClient fineractWebClient,
            FineractProperties fineractProperties) {
        this.fineractWebClient = fineractWebClient;
        this.fineractProperties = fineractProperties;
    }
    
    /**
     * Authenticate user with username and password.
     * POST /v1/authentication
     * 
     * @param request Authentication credentials
     * @return AuthResponse containing authentication token and user details
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "authenticateFallback")
    @Retry(name = "fineractApi")
    public AuthResponse authenticate(AuthRequest request) {
        log.debug("Authenticating user: {}", request.getUsername());
        
        String credentials = Base64.getEncoder()
            .encodeToString((request.getUsername() + ":" + request.getPassword()).getBytes());
        
        return fineractWebClient.post()
            .uri("/v1/authentication")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response -> {
                log.warn("Authentication failed for user: {}", request.getUsername());
                return Mono.error(new AuthenticationFailedException(
                    "Invalid username or password"));
            })
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error during authentication",
                    response.statusCode().value())))
            .bodyToMono(AuthResponse.class)
            .block();
    }
    
    /**
     * Authenticate using HTTP Basic auth variant for token generation.
     * POST /authentication?username={}&password={}
     * 
     * @param username User's username
     * @param password User's password
     * @return AuthResponse containing authentication token
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "authenticateBasicFallback")
    @Retry(name = "fineractApi")
    public AuthResponse authenticateBasic(String username, String password) {
        log.debug("Authenticating user (basic): {}", username);
        
        return fineractWebClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/authentication")
                .queryParam("username", username)
                .queryParam("password", password)
                .build())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response -> {
                log.warn("Basic authentication failed for user: {}", username);
                return Mono.error(new AuthenticationFailedException(
                    "Invalid username or password"));
            })
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error during authentication",
                    response.statusCode().value())))
            .bodyToMono(AuthResponse.class)
            .block();
    }
    
    /**
     * Authenticate and retrieve roles and permissions.
     * POST /v1/self/authentication
     * 
     * @param request Authentication credentials
     * @return AuthResponse with roles and permissions
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "authenticateFallback")
    @Retry(name = "fineractApi")
    public AuthResponse authenticateSelf(AuthRequest request) {
        log.debug("Self-authenticating user: {}", request.getUsername());
        
        String credentials = Base64.getEncoder()
            .encodeToString((request.getUsername() + ":" + request.getPassword()).getBytes());
        
        return fineractWebClient.post()
            .uri("/v1/self/authentication")
            .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response -> {
                log.warn("Self-authentication failed for user: {}", request.getUsername());
                return Mono.error(new AuthenticationFailedException(
                    "Invalid username or password"));
            })
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(new FineractApiException(
                    "Fineract server error during self-authentication",
                    response.statusCode().value())))
            .bodyToMono(AuthResponse.class)
            .block();
    }
    
    /**
     * Get Base64 encoded authentication key using configured credentials.
     * Useful for subsequent API calls that require authentication.
     * 
     * @return Base64 encoded authentication string
     */
    public String getDefaultAuthKey() {
        return Base64.getEncoder().encodeToString(
            (fineractProperties.getUsername() + ":" + fineractProperties.getPassword()).getBytes());
    }
    
    // Fallback methods for circuit breaker
    
    private AuthResponse authenticateFallback(AuthRequest request, Throwable t) {
        log.error("Circuit breaker fallback for authenticate: {}", t.getMessage());
        throw new FineractApiException(
            "Authentication service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
    
    private AuthResponse authenticateBasicFallback(String username, String password, Throwable t) {
        log.error("Circuit breaker fallback for authenticateBasic: {}", t.getMessage());
        throw new FineractApiException(
            "Authentication service is temporarily unavailable. Please try again later.",
            503, "SERVICE_UNAVAILABLE");
    }
}
