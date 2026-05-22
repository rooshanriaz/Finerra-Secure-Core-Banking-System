package com.fyp.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for verifying Decentralized Identities (DIDs) on the blockchain
 * via the KYC/AML service during authentication.
 */
@Slf4j
@Service
public class DidVerificationService {

    private final WebClient webClient;
    private final boolean verificationEnabled;

    public DidVerificationService(
            WebClient.Builder webClientBuilder,
            @Value("${did.kyc-service-url:http://kyc-aml-service:8083}") String kycServiceUrl,
            @Value("${did.verification.enabled:true}") boolean verificationEnabled) {
        this.webClient = webClientBuilder
                .baseUrl(kycServiceUrl)
                .build();
        this.verificationEnabled = verificationEnabled;
        log.info("DID verification service initialized. Enabled: {}, KYC URL: {}", verificationEnabled, kycServiceUrl);
    }

    /**
     * Verify a DID by resolving it on the blockchain and checking its credential status.
     * Returns a result object; never throws — falls back to unverified on any error.
     */
    public DidVerificationResult verifyDid(String didIdentifier) {
        if (!verificationEnabled) {
            log.debug("DID verification is disabled");
            return DidVerificationResult.disabled();
        }

        if (didIdentifier == null || didIdentifier.isBlank()) {
            return DidVerificationResult.noDid();
        }

        try {
            log.info("Verifying DID on blockchain: {}", didIdentifier);

            @SuppressWarnings("unchecked")
            Map<String, Object> didResponse = webClient.get()
                    .uri("/api/v1/did/{didId}", didIdentifier)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (didResponse == null) {
                log.warn("DID resolution returned null for: {}", didIdentifier);
                return DidVerificationResult.failed(didIdentifier, "DID not found");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) didResponse.get("data");
            if (data == null) {
                return DidVerificationResult.failed(didIdentifier, "Invalid DID response");
            }

            String status = (String) data.get("status");
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                log.warn("DID is not active: {} (status: {})", didIdentifier, status);
                return DidVerificationResult.failed(didIdentifier, "DID status: " + status);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> credentials = (List<Map<String, Object>>) data.get("credentials");
            if (credentials != null && !credentials.isEmpty()) {
                String credentialId = (String) credentials.get(0).get("id");
                if (credentialId != null) {
                    return verifyCredential(didIdentifier, credentialId);
                }
            }

            log.info("DID resolved and active (no credentials to verify): {}", didIdentifier);
            return DidVerificationResult.verified(didIdentifier, null);

        } catch (Exception e) {
            log.warn("DID verification failed for {}: {}", didIdentifier, e.getMessage());
            return DidVerificationResult.failed(didIdentifier, e.getMessage());
        }
    }

    private DidVerificationResult verifyCredential(String didIdentifier, String credentialId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> credResponse = webClient.post()
                    .uri("/api/v1/did/verify-credential")
                    .bodyValue(Map.of("credentialId", credentialId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (credResponse != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> credData = (Map<String, Object>) credResponse.get("data");
                if (credData != null) {
                    Boolean valid = (Boolean) credData.get("valid");
                    if (Boolean.TRUE.equals(valid)) {
                        log.info("DID credential verified on blockchain: {} (cred: {})", didIdentifier, credentialId);
                        return DidVerificationResult.verified(didIdentifier, credentialId);
                    }
                }
            }
            log.warn("DID credential invalid: {} (cred: {})", didIdentifier, credentialId);
            return DidVerificationResult.failed(didIdentifier, "Credential verification failed");
        } catch (Exception e) {
            log.warn("Credential verification error for {}: {}", credentialId, e.getMessage());
            return DidVerificationResult.verified(didIdentifier, null);
        }
    }

    /**
     * Result of a DID verification attempt.
     */
    public record DidVerificationResult(
            boolean verified,
            boolean enabled,
            String didId,
            String credentialId,
            String message,
            LocalDateTime timestamp
    ) {
        public static DidVerificationResult verified(String didId, String credentialId) {
            return new DidVerificationResult(true, true, didId, credentialId,
                    "DID verified on blockchain", LocalDateTime.now());
        }

        public static DidVerificationResult failed(String didId, String reason) {
            return new DidVerificationResult(false, true, didId, null, reason, LocalDateTime.now());
        }

        public static DidVerificationResult disabled() {
            return new DidVerificationResult(false, false, null, null,
                    "DID verification disabled", LocalDateTime.now());
        }

        public static DidVerificationResult noDid() {
            return new DidVerificationResult(false, true, null, null,
                    "No DID linked to account", LocalDateTime.now());
        }
    }
}
