package com.fyp.fraud.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.fraud.FraudDetectionServiceApplication;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end fraud API with a stubbed Python ML /predict endpoint.
 */
@SpringBootTest(classes = FraudDetectionServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("fraud-it")
@Import(RiskScoringMlIntegrationTest.IntegrationSecurity.class)
class RiskScoringMlIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MockWebServer ML_SERVER = new MockWebServer();

    static {
        try {
            ML_SERVER.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void mlUrl(DynamicPropertyRegistry registry) {
        registry.add("ml-service.url", () -> "http://127.0.0.1:" + ML_SERVER.getPort());
    }

    @AfterAll
    static void stopMl() throws Exception {
        ML_SERVER.shutdown();
    }

    @Test
    void scoreUsesMlServiceAndReturnsAllowForLowRisk() throws Exception {
        String mlBody = """
                {"risk_score":0.08,"risk_level":"LOW","risk_factors":[],"rf_probability":0.05,"isolation_score":0.12}
                """;
        ML_SERVER.enqueue(new MockResponse()
                .setBody(mlBody)
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

        String base = "http://127.0.0.1:" + port + "/api";
        Map<String, Object> body = Map.of(
                "transactionId", "IT-TXN-001",
                "accountId", 1001L,
                "transactionType", "DEPOSIT",
                "amount", 500,
                "initiatedBy", "integration-test"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = restTemplate.postForEntity(
                base + "/v1/fraud/score",
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode root = MAPPER.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isTrue();
        JsonNode data = root.path("data");
        assertThat(data.path("recommendation").asText()).isEqualTo("ALLOW");
        assertThat(data.path("riskScore").asDouble()).isGreaterThanOrEqualTo(0);
        assertThat(ML_SERVER.takeRequest().getPath()).endsWith("/predict");
    }

    @Test
    void scoreReturnsBlockWhenMlRiskIsHigh() throws Exception {
        String mlBody = """
                {"risk_score":0.95,"risk_level":"CRITICAL","risk_factors":["high_amount_ratio"],"rf_probability":0.95,"isolation_score":0.9}
                """;
        ML_SERVER.enqueue(new MockResponse()
                .setBody(mlBody)
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

        String base = "http://127.0.0.1:" + port + "/api";
        Map<String, Object> body = Map.of(
                "transactionId", "IT-TXN-002",
                "accountId", 1002L,
                "transactionType", "WITHDRAWAL",
                "amount", 900000,
                "initiatedBy", "integration-test"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = restTemplate.postForEntity(
                base + "/v1/fraud/score",
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode data = MAPPER.readTree(res.getBody()).path("data");
        assertThat(data.path("recommendation").asText()).isEqualTo("BLOCK");
        assertThat(data.path("alertId").asText()).isNotBlank();
    }

    @Configuration
    static class IntegrationSecurity {

        @Bean
        @Order(1)
        SecurityFilterChain integration(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
            http.csrf(c -> c.disable());
            return http.build();
        }

        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            byte[] keyBytes = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
            SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
            return NimbusJwtDecoder.withSecretKey(key).build();
        }
    }
}
