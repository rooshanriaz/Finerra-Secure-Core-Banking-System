package com.fyp.kyc.service;

import com.fyp.kyc.dto.KycOnboardRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for Core Banking Connector (CBC) service.
 * Creates Fineract clients after successful KYC verification.
 */
@Slf4j
@Service
public class CbcClientService {

    private final WebClient webClient;

    public CbcClientService(WebClient.Builder webClientBuilder,
                            @Value("${services.core-banking-connector.url}") String cbcUrl,
                            @Value("${services.core-banking-connector.username}") String username,
                            @Value("${services.core-banking-connector.password}") String password) {
        this.webClient = webClientBuilder
            .baseUrl(cbcUrl)
            .defaultHeaders(headers -> headers.setBasicAuth(username, password))
            .build();
        log.info("CBC client configured with URL: {}", cbcUrl);
    }

    /**
     * Create a client in Fineract via CBC after KYC approval.
     * 
     * @param request KYC onboard request with client details
     * @param didId   DID identifier to store as external ID
     * @return Map containing the created client details (including Fineract clientId)
     */
    @CircuitBreaker(name = "cbcService", fallbackMethod = "createClientFallback")
    public Map<String, Object> createFineractClient(KycOnboardRequest request, String didId) {
        log.info("Creating Fineract client via CBC for DID: {}", didId);

        // Build Fineract client creation payload
        Map<String, Object> clientPayload = new LinkedHashMap<>();
        clientPayload.put("officeId", request.getOfficeId() != null ? request.getOfficeId() : 1);
        clientPayload.put("firstname", request.getFirstName());
        clientPayload.put("lastname", request.getLastName());
        clientPayload.put("externalId", didId); // Link DID as external ID
        clientPayload.put("active", true);
        clientPayload.put("locale", "en");
        clientPayload.put("dateFormat", "dd MMMM yyyy");

        // Format activation date
        LocalDate activationDate = LocalDate.now();
        clientPayload.put("activationDate", activationDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));

        if (request.getMobileNumber() != null) {
            clientPayload.put("mobileNo", request.getMobileNumber());
        }

        // Parse DOB
        if (request.getDateOfBirth() != null) {
            LocalDate dob = LocalDate.parse(request.getDateOfBirth());
            clientPayload.put("dateOfBirth", dob.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));
        }

        Map<String, Object> response = webClient.post()
            .uri("/v1/clients")
            .bodyValue(clientPayload)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        log.info("Fineract client creation response received");
        return response;
    }

    /**
     * Fallback when CBC is unavailable.
     */
    @SuppressWarnings("unused")
    private Map<String, Object> createClientFallback(KycOnboardRequest request, String didId, Throwable t) {
        log.warn("CBC service unavailable, using fallback. Error: {}", t.getMessage());
        return Map.of(
            "success", false,
            "message", "Core Banking Connector unavailable. Client creation pending.",
            "data", Map.of("clientId", -1L, "status", "PENDING")
        );
    }
}
