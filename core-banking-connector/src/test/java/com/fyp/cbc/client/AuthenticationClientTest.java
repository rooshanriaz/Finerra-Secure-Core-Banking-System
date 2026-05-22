package com.fyp.cbc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fyp.cbc.config.FineractProperties;
import com.fyp.cbc.dto.request.AuthRequest;
import com.fyp.cbc.dto.response.AuthResponse;
import com.fyp.cbc.exception.AuthenticationFailedException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * Unit tests for AuthenticationClient.
 * Uses MockWebServer to simulate Fineract API responses.
 */
class AuthenticationClientTest {
    
    private MockWebServer mockWebServer;
    private AuthenticationClient authenticationClient;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        FineractProperties properties = new FineractProperties();
        properties.setBaseUrl(mockWebServer.url("/").toString());
        properties.setUsername("mifos");
        properties.setPassword("password");
        properties.setTenantId("default");
        
        WebClient webClient = WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())
            .defaultHeader("fineract-platform-tenantid", "default")
            .build();
        
        authenticationClient = new AuthenticationClient(webClient, properties);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    @DisplayName("Should authenticate successfully with valid credentials")
    void authenticate_ValidCredentials_ReturnsAuthResponse() {
        // Given
        String responseJson = """
            {
                "username": "mifos",
                "userId": 1,
                "base64EncodedAuthenticationKey": "bWlmb3M6cGFzc3dvcmQ=",
                "authenticated": true,
                "officeId": 1,
                "officeName": "Head Office",
                "roles": [{"id": 1, "name": "Super User"}],
                "permissions": ["ALL_FUNCTIONS"]
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));
        
        AuthRequest request = AuthRequest.builder()
            .username("mifos")
            .password("password")
            .build();
        
        // When
        AuthResponse response = authenticationClient.authenticate(request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.getUsername()).isEqualTo("mifos");
        assertThat(response.getBase64EncodedAuthenticationKey()).isEqualTo("bWlmb3M6cGFzc3dvcmQ=");
        assertThat(response.getRoles()).hasSize(1);
        assertThat(response.getPermissions()).contains("ALL_FUNCTIONS");
    }
    
    @Test
    @DisplayName("Should throw AuthenticationFailedException for invalid credentials")
    void authenticate_InvalidCredentials_ThrowsException() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody("{\"errors\": [{\"developerMessage\": \"Invalid credentials\"}]}")
            .addHeader("Content-Type", "application/json"));
        
        AuthRequest request = AuthRequest.builder()
            .username("wrong")
            .password("wrong")
            .build();
        
        // When/Then
        assertThatThrownBy(() -> authenticationClient.authenticate(request))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessageContaining("Invalid");
    }
    
    @Test
    @DisplayName("Should return encoded auth key using default credentials")
    void getDefaultAuthKey_ReturnsBase64EncodedKey() {
        // When
        String authKey = authenticationClient.getDefaultAuthKey();
        
        // Then
        assertThat(authKey).isNotNull();
        assertThat(authKey).isNotEmpty();
        // mifos:password in base64
        assertThat(authKey).isEqualTo("bWlmb3M6cGFzc3dvcmQ=");
    }
    
    @Test
    @DisplayName("Should authenticate self and return roles and permissions")
    void authenticateSelf_ValidCredentials_ReturnsRolesAndPermissions() {
        // Given
        String responseJson = """
            {
                "username": "mifos",
                "userId": 1,
                "base64EncodedAuthenticationKey": "bWlmb3M6cGFzc3dvcmQ=",
                "authenticated": true,
                "officeId": 1,
                "officeName": "Head Office",
                "roles": [
                    {"id": 1, "name": "Super User"},
                    {"id": 2, "name": "Loan Officer"}
                ],
                "permissions": ["ALL_FUNCTIONS", "CREATE_CLIENT", "READ_LOAN"]
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));
        
        AuthRequest request = AuthRequest.builder()
            .username("mifos")
            .password("password")
            .build();
        
        // When
        AuthResponse response = authenticationClient.authenticateSelf(request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.getRoles()).hasSize(2);
        assertThat(response.getPermissions()).hasSize(3);
    }
}
