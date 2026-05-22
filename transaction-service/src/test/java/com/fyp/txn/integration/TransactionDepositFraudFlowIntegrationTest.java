package com.fyp.txn.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.txn.TransactionServiceApplication;
import com.fyp.txn.dto.TransactionRequest;
import com.fyp.txn.service.AuditClientService;
import com.fyp.txn.service.MachineTokenService;
import io.jsonwebtoken.Jwts;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Deposit flow: transaction-service calls fraud-detection /score, then CBC deposit.
 */
@SpringBootTest(classes = TransactionServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("txn-it")
@Import(TransactionDepositFraudFlowIntegrationTest.IntegrationSecurity.class)
class TransactionDepositFraudFlowIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final MockWebServer FRAUD_SERVER = new MockWebServer();
    private static final MockWebServer CBC_SERVER = new MockWebServer();

    static {
        try {
            FRAUD_SERVER.start();
            CBC_SERVER.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    MachineTokenService machineTokenService;

    @MockBean
    AuditClientService auditClientService;

    @DynamicPropertySource
    static void backendUrls(DynamicPropertyRegistry registry) {
        registry.add("services.fraud-detection-service.url",
                () -> "http://127.0.0.1:" + FRAUD_SERVER.getPort() + "/api");
        registry.add("services.core-banking-connector.url",
                () -> "http://127.0.0.1:" + CBC_SERVER.getPort() + "/api");
    }

    @AfterAll
    static void stopServers() throws IOException {
        FRAUD_SERVER.shutdown();
        CBC_SERVER.shutdown();
    }

    @Test
    void depositCallsFraudThenCbcWhenRiskAllows() throws Exception {
        when(machineTokenService.getAccessToken()).thenReturn("machine-test-token");

        String fraudJson = """
                {"success":true,"data":{"transactionId":"ignored","riskScore":0.11,"riskLevel":"LOW","recommendation":"ALLOW","riskFactors":[],"alertId":null}}
                """;
        String cbcJson = """
                {"success":true,"data":{"resourceId":4242}}
                """;
        FRAUD_SERVER.enqueue(new MockResponse()
                .setBody(fraudJson)
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
        CBC_SERVER.enqueue(new MockResponse()
                .setBody(cbcJson)
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

        long fraudBefore = FRAUD_SERVER.getRequestCount();
        long cbcBefore = CBC_SERVER.getRequestCount();

        TransactionRequest req = TransactionRequest.builder()
                .transactionAmount(new BigDecimal("250.00"))
                .transactionDate("09 February 2026")
                .note("integration")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(IntegrationSecurity.bearerToken());

        String url = "http://127.0.0.1:" + port + "/api/v1/transactions/savings/1/deposit";
        ResponseEntity<String> res = restTemplate.postForEntity(
                url,
                new HttpEntity<>(req, headers),
                String.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode root = MAPPER.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isTrue();
        JsonNode data = root.path("data");
        assertThat(data.path("status").asText()).isEqualTo("COMPLETED");

        assertThat(FRAUD_SERVER.getRequestCount()).isEqualTo(fraudBefore + 1);
        assertThat(CBC_SERVER.getRequestCount()).isEqualTo(cbcBefore + 1);
    }

    @Test
    void depositStopsBeforeCbcWhenFraudRecommendsBlock() throws Exception {
        when(machineTokenService.getAccessToken()).thenReturn("machine-test-token");

        String fraudJson = """
                {"success":true,"data":{"transactionId":"ignored","riskScore":0.92,"riskLevel":"CRITICAL","recommendation":"BLOCK","riskFactors":["test"],"alertId":"ALERT-IT-1"}}
                """;
        FRAUD_SERVER.enqueue(new MockResponse()
                .setBody(fraudJson)
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

        long cbcBefore = CBC_SERVER.getRequestCount();

        TransactionRequest req = TransactionRequest.builder()
                .transactionAmount(new BigDecimal("100.00"))
                .transactionDate("09 February 2026")
                .note("blocked-flow")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(IntegrationSecurity.bearerToken());

        String url = "http://127.0.0.1:" + port + "/api/v1/transactions/savings/2/deposit";
        ResponseEntity<String> res = restTemplate.postForEntity(
                url,
                new HttpEntity<>(req, headers),
                String.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode data = MAPPER.readTree(res.getBody()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("FRAUD_BLOCKED");
        assertThat(CBC_SERVER.getRequestCount()).isEqualTo(cbcBefore);
    }

    @Configuration
    static class IntegrationSecurity {

        private static final SecretKeySpec JWT_KEY = new SecretKeySpec(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");

        static String bearerToken() {
            return Jwts.builder()
                    .subject("it-user")
                    .claim("realm_access", Map.of("roles", List.of("MANAGER")))
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(JWT_KEY)
                    .compact();
        }

        @Bean
        @Order(1)
        SecurityFilterChain integration(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated());
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
            http.csrf(c -> c.disable());
            return http.build();
        }

        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withSecretKey(JWT_KEY).build();
        }
    }
}
