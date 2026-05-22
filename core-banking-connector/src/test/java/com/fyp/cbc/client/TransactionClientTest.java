package com.fyp.cbc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.fyp.cbc.dto.request.TransactionRequest;
import com.fyp.cbc.dto.response.TransactionResponse;
import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.exception.ResourceNotFoundException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Unit tests for TransactionClient.
 * Uses MockWebServer to simulate Fineract API responses.
 */
@ExtendWith(MockitoExtension.class)
class TransactionClientTest {
    
    private MockWebServer mockWebServer;
    private TransactionClient transactionClient;
    
    @Mock
    private AuthenticationClient authClient;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        WebClient webClient = WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())
            .defaultHeader("fineract-platform-tenantid", "default")
            .build();
        
        when(authClient.getDefaultAuthKey()).thenReturn("bWlmb3M6cGFzc3dvcmQ=");
        
        transactionClient = new TransactionClient(webClient, authClient);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    @DisplayName("Should deposit successfully")
    void deposit_ValidRequest_ReturnsTransactionResponse() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "officeId": 1,
                "clientId": 1,
                "savingsId": 1,
                "resourceId": 100,
                "changes": {
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "transactionDate": "01 January 2024",
                    "transactionAmount": 1000.00
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));
        
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(new BigDecimal("1000.00"))
            .transactionDate("01 January 2024")
            .build();
        
        // When
        TransactionResponse response = transactionClient.deposit(1L, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getResourceId()).isEqualTo(100L);
        assertThat(response.getSavingsId()).isEqualTo(1L);
        
        // Verify request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).contains("/v1/savingsaccounts/1/transactions");
        assertThat(recordedRequest.getPath()).contains("command=deposit");
    }
    
    @Test
    @DisplayName("Should throw ResourceNotFoundException when account not found")
    void deposit_AccountNotFound_ThrowsException() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .addHeader("Content-Type", "application/json"));
        
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(new BigDecimal("1000.00"))
            .transactionDate("01 January 2024")
            .build();
        
        // When/Then
        assertThatThrownBy(() -> transactionClient.deposit(999L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("SavingsAccount");
    }
    
    @Test
    @DisplayName("Should withdraw successfully")
    void withdraw_ValidRequest_ReturnsTransactionResponse() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "officeId": 1,
                "clientId": 1,
                "savingsId": 1,
                "resourceId": 101,
                "changes": {
                    "locale": "en",
                    "transactionDate": "01 January 2024",
                    "transactionAmount": 500.00
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));
        
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(new BigDecimal("500.00"))
            .transactionDate("01 January 2024")
            .build();
        
        // When
        TransactionResponse response = transactionClient.withdraw(1L, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getResourceId()).isEqualTo(101L);
        
        // Verify request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).contains("command=withdrawal");
    }
    
    @Test
    @DisplayName("Should process loan repayment successfully")
    void loanRepayment_ValidRequest_ReturnsTransactionResponse() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "officeId": 1,
                "clientId": 1,
                "resourceId": 102
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));
        
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(new BigDecimal("250.00"))
            .transactionDate("01 January 2024")
            .build();
        
        // When
        TransactionResponse response = transactionClient.loanRepayment(1L, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getResourceId()).isEqualTo(102L);
        
        // Verify request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).contains("/v1/loans/1/transactions");
        assertThat(recordedRequest.getPath()).contains("command=repayment");
    }
    
    @Test
    @DisplayName("Should handle Fineract API errors gracefully")
    void deposit_ServerError_ThrowsFineractApiException() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("{\"message\": \"Internal server error\"}")
            .addHeader("Content-Type", "application/json"));
        
        TransactionRequest request = TransactionRequest.builder()
            .transactionAmount(new BigDecimal("1000.00"))
            .transactionDate("01 January 2024")
            .build();
        
        // When/Then
        assertThatThrownBy(() -> transactionClient.deposit(1L, request))
            .isInstanceOf(FineractApiException.class);
    }
    
    @Test
    @DisplayName("Should retrieve journal entries")
    void getJournalEntries_ValidRequest_ReturnsEntries() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "totalFilteredRecords": 10,
                "pageItems": [
                    {
                        "id": 1,
                        "officeId": 1,
                        "officeName": "Head Office",
                        "glAccountName": "Cash",
                        "glAccountId": 1,
                        "transactionDate": "2024-01-01",
                        "amount": 1000.00
                    }
                ]
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));
        
        // When
        var response = transactionClient.getJournalEntries(0, 10, null, null, null);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalFilteredRecords()).isEqualTo(10L);
        assertThat(response.getPageItems()).hasSize(1);
        
        // Verify request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).contains("/v1/journalentries");
    }
}
