package com.fyp.kyc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class MachineTokenService {
    private final WebClient webClient = WebClient.builder().build();
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedAccessToken;
    private volatile Instant cachedExpiry = Instant.EPOCH;

    public MachineTokenService(
            @Value("${security.oauth2.client.token-url:http://localhost:8090/realms/finnera/protocol/openid-connect/token}") String tokenUrl,
            @Value("${security.oauth2.client.client-id:transaction-service}") String clientId,
            @Value("${security.oauth2.client.client-secret:transaction-service-secret-change-me}") String clientSecret) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public synchronized String getAccessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(cachedExpiry.minusSeconds(20))) {
            return cachedAccessToken;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters
                        .fromFormData("grant_type", "client_credentials")
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw new IllegalStateException("Unable to obtain client-credentials access token for KYC service");
        }

        cachedAccessToken = String.valueOf(tokenResponse.get("access_token"));
        long expiresIn = tokenResponse.get("expires_in") instanceof Number n ? n.longValue() : 60L;
        cachedExpiry = Instant.now().plusSeconds(expiresIn);
        log.debug("Fetched machine token for KYC service; expires in {}s", expiresIn);
        return cachedAccessToken;
    }
}

