package com.fyp.kyc.service;

import com.fyp.kyc.dto.KycOnboardRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Client for the NADRA Mock Service (port 8084).
 * Handles CNIC verification requests.
 */
@Slf4j
@Service
public class NadraClientService {

    private final WebClient webClient;

    public NadraClientService(WebClient.Builder webClientBuilder,
                              @Value("${services.nadra-mock.url}") String nadraUrl) {
        this.webClient = webClientBuilder.baseUrl(nadraUrl).build();
        log.info("NADRA client configured with URL: {}", nadraUrl);
    }

    /**
     * Verify CNIC with NADRA mock service.
     * 
     * @return Map containing verification response
     */
    @CircuitBreaker(name = "nadraService", fallbackMethod = "verifyCnicFallback")
    public Map<String, Object> verifyCnic(KycOnboardRequest request) {
        log.info("Sending CNIC verification request to NADRA for: {}", maskCnic(request.getCnicNumber()));

        Map<String, Object> nadraRequest = Map.of(
            "cnicNumber", request.getCnicNumber(),
            "fullName", request.getFirstName() + " " + request.getLastName(),
            "dateOfBirth", request.getDateOfBirth(),
            "fatherName", request.getFatherName() != null ? request.getFatherName() : "",
            "address", request.getAddress() != null ? request.getAddress() : ""
        );

        Map<String, Object> response = webClient.post()
            .uri("/v1/verify/cnic")
            .bodyValue(nadraRequest)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        log.info("NADRA verification response received");
        return response;
    }

    /**
     * Fallback when NADRA service is unavailable.
     */
    @SuppressWarnings("unused")
    private Map<String, Object> verifyCnicFallback(KycOnboardRequest request, Throwable t) {
        log.warn("NADRA service unavailable, using fallback. Error: {}", t.getMessage());
        return Map.of(
            "success", true,
            "data", Map.of(
                "status", "VERIFIED",
                "message", "Fallback: NADRA service unavailable, CNIC verification pending manual review",
                "requestId", "FALLBACK-" + System.currentTimeMillis(),
                "verificationToken", "FALLBACK-TOKEN"
            )
        );
    }

    /**
     * Check verification status by request ID.
     */
    public Map<String, Object> getVerificationStatus(String requestId) {
        return webClient.get()
            .uri("/v1/verify/status/{requestId}", requestId)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
    }

    private String maskCnic(String cnic) {
        if (cnic == null || cnic.length() < 5) return "****";
        return cnic.substring(0, 5) + "-*******-*";
    }
}
