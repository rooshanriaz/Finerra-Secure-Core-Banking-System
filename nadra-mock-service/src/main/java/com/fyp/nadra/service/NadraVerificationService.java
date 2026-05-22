package com.fyp.nadra.service;

import com.fyp.nadra.config.NadraProperties;
import com.fyp.nadra.dto.BiometricVerificationRequest;
import com.fyp.nadra.dto.CnicVerificationRequest;
import com.fyp.nadra.dto.VerificationResponse;
import com.fyp.nadra.dto.VerificationResponse.PersonDetails;
import com.fyp.nadra.dto.VerificationResponse.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class NadraVerificationService {

    private final NadraProperties nadraProperties;

    // In-memory store for verification requests (simulates async processing)
    private final Map<String, VerificationResponse> verificationStore = new ConcurrentHashMap<>();

    /**
     * Verify CNIC number against mock NADRA database.
     * Certain CNIC prefixes always pass/fail for demo purposes.
     */
    public VerificationResponse verifyCnic(CnicVerificationRequest request) {
        String requestId = UUID.randomUUID().toString();
        String cleanCnic = request.getCnicNumber().replace("-", "");
        
        log.info("Processing CNIC verification request [{}] for CNIC: {}", requestId, maskCnic(request.getCnicNumber()));

        // Simulate processing delay
        simulateDelay();

        // Determine verification result based on CNIC prefix
        VerificationStatus status = determineStatus(cleanCnic);
        
        VerificationResponse response;
        
        if (status == VerificationStatus.VERIFIED) {
            response = VerificationResponse.builder()
                .requestId(requestId)
                .cnicNumber(request.getCnicNumber())
                .status(VerificationStatus.VERIFIED)
                .verificationToken(generateVerificationToken(requestId))
                .message("CNIC verification successful. Identity confirmed.")
                .matchScore(0.95 + ThreadLocalRandom.current().nextDouble(0.05))
                .personDetails(generateMockPersonDetails(request))
                .timestamp(LocalDateTime.now())
                .build();
            log.info("CNIC verification PASSED for request [{}]", requestId);
        } else if (status == VerificationStatus.REJECTED) {
            response = VerificationResponse.builder()
                .requestId(requestId)
                .cnicNumber(request.getCnicNumber())
                .status(VerificationStatus.REJECTED)
                .message("CNIC verification failed. Identity could not be confirmed.")
                .matchScore(ThreadLocalRandom.current().nextDouble(0.3))
                .timestamp(LocalDateTime.now())
                .build();
            log.warn("CNIC verification REJECTED for request [{}]", requestId);
        } else {
            // Random pass/fail for non-configured ranges
            boolean pass = ThreadLocalRandom.current().nextBoolean();
            if (pass) {
                response = VerificationResponse.builder()
                    .requestId(requestId)
                    .cnicNumber(request.getCnicNumber())
                    .status(VerificationStatus.VERIFIED)
                    .verificationToken(generateVerificationToken(requestId))
                    .message("CNIC verification successful.")
                    .matchScore(0.80 + ThreadLocalRandom.current().nextDouble(0.15))
                    .personDetails(generateMockPersonDetails(request))
                    .timestamp(LocalDateTime.now())
                    .build();
            } else {
                response = VerificationResponse.builder()
                    .requestId(requestId)
                    .cnicNumber(request.getCnicNumber())
                    .status(VerificationStatus.NOT_FOUND)
                    .message("CNIC not found in NADRA database.")
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        }

        // Store for status lookup
        verificationStore.put(requestId, response);
        return response;
    }

    /**
     * Simulate biometric verification.
     */
    public VerificationResponse verifyBiometric(BiometricVerificationRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("Processing biometric verification request [{}] for CNIC: {}", requestId, maskCnic(request.getCnicNumber()));

        simulateDelay();

        // Simulate biometric match score
        double matchScore = 0.5 + ThreadLocalRandom.current().nextDouble(0.5);
        boolean matched = matchScore >= nadraProperties.getBiometricThreshold();

        VerificationResponse response = VerificationResponse.builder()
            .requestId(requestId)
            .cnicNumber(request.getCnicNumber())
            .status(matched ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED)
            .verificationToken(matched ? generateVerificationToken(requestId) : null)
            .message(matched 
                ? "Biometric verification successful. Match score: " + String.format("%.2f", matchScore)
                : "Biometric verification failed. Match score below threshold.")
            .matchScore(matchScore)
            .timestamp(LocalDateTime.now())
            .build();

        verificationStore.put(requestId, response);
        log.info("Biometric verification {} for request [{}], score: {}", 
            matched ? "PASSED" : "FAILED", requestId, String.format("%.2f", matchScore));
        return response;
    }

    /**
     * Get verification status by request ID.
     */
    public VerificationResponse getVerificationStatus(String requestId) {
        VerificationResponse response = verificationStore.get(requestId);
        if (response == null) {
            return VerificationResponse.builder()
                .requestId(requestId)
                .status(VerificationStatus.NOT_FOUND)
                .message("Verification request not found.")
                .timestamp(LocalDateTime.now())
                .build();
        }
        return response;
    }

    private VerificationStatus determineStatus(String cleanCnic) {
        for (String prefix : nadraProperties.getPassPrefixes()) {
            if (cleanCnic.startsWith(prefix)) {
                return VerificationStatus.VERIFIED;
            }
        }
        for (String prefix : nadraProperties.getFailPrefixes()) {
            if (cleanCnic.startsWith(prefix)) {
                return VerificationStatus.REJECTED;
            }
        }
        return null; // Will be randomly decided
    }

    private void simulateDelay() {
        try {
            int delay = ThreadLocalRandom.current().nextInt(
                nadraProperties.getMinDelayMs(), nadraProperties.getMaxDelayMs());
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String generateVerificationToken(String requestId) {
        return "NADRA-VT-" + UUID.nameUUIDFromBytes(requestId.getBytes()).toString().substring(0, 8).toUpperCase();
    }

    private PersonDetails generateMockPersonDetails(CnicVerificationRequest request) {
        return PersonDetails.builder()
            .fullName(request.getFullName())
            .fatherName(request.getFatherName() != null ? request.getFatherName() : "Muhammad Ahmed")
            .dateOfBirth(request.getDateOfBirth())
            .gender("M")
            .address(request.getAddress() != null ? request.getAddress() : "House # 123, Street 5, Islamabad")
            .issuanceDate(LocalDate.now().minusYears(3).toString())
            .expiryDate(LocalDate.now().plusYears(7).toString())
            .build();
    }

    private String maskCnic(String cnic) {
        if (cnic == null || cnic.length() < 5) return "****";
        return cnic.substring(0, 5) + "-*******-*";
    }
}
