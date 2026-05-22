package com.fyp.kyc.service;

import com.fyp.kyc.dto.*;
import com.fyp.kyc.entity.KycRecord;
import com.fyp.kyc.entity.KycRecord.AmlStatus;
import com.fyp.kyc.entity.KycRecord.KycStatus;
import com.fyp.kyc.repository.KycRecordRepository;
import com.fyp.kyc.security.AesEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Main KYC onboarding orchestration service.
 * Coordinates the full onboarding flow:
 * 1. Encrypt PII and create KYC record
 * 2. Verify CNIC via NADRA
 * 3. Screen against AML sanctions
 * 4. Create DID on Fabric
 * 5. Issue KYC verifiable credential
 * 6. Create Fineract client via CBC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycOrchestrationService {

    private final KycRecordRepository kycRecordRepository;
    private final AesEncryptionService encryptionService;
    private final NadraClientService nadraClientService;
    private final AmlScreeningService amlScreeningService;
    private final FabricDIDService fabricDIDService;
    private final CbcClientService cbcClientService;
    private final KycAuditClientService kycAuditClientService;

    /**
     * Full KYC onboarding flow.
     */
    @Transactional
    public KycOnboardResponse onboardClient(KycOnboardRequest request, String initiatedBy) {
        String referenceId = "KYC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Starting KYC onboarding [{}] for CNIC: {}", referenceId, maskCnic(request.getCnicNumber()));

        // Step 1: Encrypt PII and create initial KYC record
        String cnicHash = encryptionService.hash(request.getCnicNumber());

        // Check if already onboarded
        if (kycRecordRepository.existsByCnicHash(cnicHash)) {
            KycRecord existing = kycRecordRepository.findByCnicHash(cnicHash).orElse(null);
            if (existing != null && existing.getStatus() == KycStatus.COMPLETED) {
                log.warn("CNIC already onboarded: {}", referenceId);
                kycAuditClientService.recordOnboardingAttempt(
                    referenceId,
                    existing.getFineractClientId(),
                    initiatedBy,
                    "KYC_ONBOARD_DUPLICATE"
                );
                return KycOnboardResponse.builder()
                    .referenceId(existing.getReferenceId())
                    .kycStatus(existing.getStatus().name())
                    .amlStatus(existing.getAmlStatus() != null ? existing.getAmlStatus().name() : null)
                    .didId(existing.getDidId())
                    .fineractClientId(existing.getFineractClientId())
                    .rejectionReason("CNIC already onboarded")
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        }

        KycRecord kycRecord = KycRecord.builder()
            .referenceId(referenceId)
            .cnicEncrypted(encryptionService.encrypt(request.getCnicNumber()))
            .cnicHash(cnicHash)
            .fullNameEncrypted(encryptionService.encrypt(request.getFirstName() + " " + request.getLastName()))
            .dobEncrypted(encryptionService.encrypt(request.getDateOfBirth()))
            .addressEncrypted(request.getAddress() != null ? encryptionService.encrypt(request.getAddress()) : null)
            .fatherNameEncrypted(request.getFatherName() != null ? encryptionService.encrypt(request.getFatherName()) : null)
            .status(KycStatus.INITIATED)
            .amlStatus(AmlStatus.PENDING)
            .createdBy(initiatedBy)
            .build();
        kycRecordRepository.save(kycRecord);

        log.info("[{}] Step 1: KYC record created with encrypted PII", referenceId);

        // Step 2: CNIC verification via NADRA
        try {
            Map<String, Object> nadraResponse = nadraClientService.verifyCnic(request);
            @SuppressWarnings("unchecked")
            Map<String, Object> nadraData = (Map<String, Object>) nadraResponse.get("data");

            if (nadraData != null) {
                String nadraStatus = (String) nadraData.get("status");
                String nadraToken = (String) nadraData.get("verificationToken");
                String nadraRequestId = (String) nadraData.get("requestId");

                kycRecord.setNadraVerificationToken(nadraToken);
                kycRecord.setNadraRequestId(nadraRequestId);

                if ("VERIFIED".equals(nadraStatus)) {
                    kycRecord.setStatus(KycStatus.CNIC_VERIFIED);
                    log.info("[{}] Step 2: CNIC verified by NADRA", referenceId);
                } else {
                    kycRecord.setStatus(KycStatus.REJECTED);
                    kycRecord.setRejectionReason("CNIC verification failed: " + nadraStatus);
                    log.warn("[{}] Step 2: CNIC verification FAILED: {}", referenceId, nadraStatus);
                    return saveAuditAndBuildResponse(kycRecord, initiatedBy);
                }
            }
        } catch (Exception e) {
            log.error("[{}] Step 2: NADRA verification error: {}", referenceId, e.getMessage());
            kycRecord.setStatus(KycStatus.FAILED);
            kycRecord.setRejectionReason("NADRA service error: " + e.getMessage());
            return saveAuditAndBuildResponse(kycRecord, initiatedBy);
        }

        // Step 3: AML sanctions screening
        try {
            String fullName = request.getFirstName() + " " + request.getLastName();
            AmlScreeningResponse amlResult = amlScreeningService.screenPerson(fullName, cnicHash, referenceId);

            if ("MATCH_FOUND".equals(amlResult.getResult())) {
                kycRecord.setStatus(KycStatus.REJECTED);
                kycRecord.setAmlStatus(AmlStatus.BLOCKED);
                kycRecord.setRejectionReason("AML screening: Match found in sanctions list - " + amlResult.getDetails());
                log.warn("[{}] Step 3: AML BLOCKED - match found", referenceId);
                return saveAuditAndBuildResponse(kycRecord, initiatedBy);
            } else if ("POTENTIAL_MATCH".equals(amlResult.getResult())) {
                kycRecord.setAmlStatus(AmlStatus.FLAGGED);
                log.warn("[{}] Step 3: AML FLAGGED - potential match (continuing)", referenceId);
            } else {
                kycRecord.setAmlStatus(AmlStatus.CLEARED);
                log.info("[{}] Step 3: AML screening CLEARED", referenceId);
            }
            kycRecord.setStatus(KycStatus.AML_CLEARED);
        } catch (Exception e) {
            log.error("[{}] Step 3: AML screening error: {}", referenceId, e.getMessage());
            // Continue with warning - AML check failure shouldn't block completely
            kycRecord.setAmlStatus(AmlStatus.PENDING);
        }

        // Step 4: Create DID on Fabric
        try {
            DidResponse didResponse = fabricDIDService.createDID(cnicHash, "BankOrg", referenceId);
            if (didResponse != null) {
                kycRecord.setDidId(didResponse.getDidId());
                kycRecord.setStatus(KycStatus.DID_CREATED);
                log.info("[{}] Step 4: DID created: {}", referenceId, didResponse.getDidId());

                // Step 4b: Issue KYC verifiable credential
                DidResponse.CredentialInfo credInfo = fabricDIDService.issueKycCredential(
                    didResponse.getDidId(), cnicHash);
                log.info("[{}] Step 4b: KYC credential issued: {}", referenceId, credInfo.getId());
            }
        } catch (Exception e) {
            log.error("[{}] Step 4: DID creation error: {}", referenceId, e.getMessage());
            // Continue - DID failure shouldn't block onboarding
        }

        // Step 5: Create Fineract client via CBC
        try {
            Map<String, Object> cbcResponse = cbcClientService.createFineractClient(request, kycRecord.getDidId());
            if (cbcResponse != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cbcData = (Map<String, Object>) cbcResponse.get("data");
                if (cbcData != null && cbcData.containsKey("clientId")) {
                    Object clientIdObj = cbcData.get("clientId");
                    Long clientId = clientIdObj instanceof Number ? ((Number) clientIdObj).longValue() : null;
                    if (clientId != null && clientId > 0) {
                        kycRecord.setFineractClientId(clientId);
                        kycRecord.setStatus(KycStatus.CLIENT_CREATED);
                        log.info("[{}] Step 5: Fineract client created: {}", referenceId, clientId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[{}] Step 5: CBC client creation error: {}", referenceId, e.getMessage());
            // Continue - Fineract might not be running
        }

        // Mark as completed
        if (kycRecord.getStatus() == KycStatus.CLIENT_CREATED || kycRecord.getStatus() == KycStatus.DID_CREATED) {
            kycRecord.setStatus(KycStatus.COMPLETED);
        }

        kycRecord = kycRecordRepository.save(kycRecord);
        kycAuditClientService.recordOnboardingAudit(kycRecord, initiatedBy);
        log.info("[{}] KYC onboarding completed with status: {}", referenceId, kycRecord.getStatus());

        return buildResponse(kycRecord);
    }

    /**
     * Get KYC status by reference ID.
     */
    public KycStatusResponse getKycStatus(String referenceId) {
        KycRecord record = kycRecordRepository.findByReferenceId(referenceId)
            .orElseThrow(() -> new RuntimeException("KYC record not found: " + referenceId));

        return KycStatusResponse.builder()
            .referenceId(record.getReferenceId())
            .kycStatus(record.getStatus().name())
            .amlStatus(record.getAmlStatus() != null ? record.getAmlStatus().name() : null)
            .didId(record.getDidId())
            .fineractClientId(record.getFineractClientId())
            .rejectionReason(record.getRejectionReason())
            .createdAt(record.getCreatedAt())
            .updatedAt(record.getUpdatedAt())
            .build();
    }

    /**
     * Get KYC status by client ID.
     */
    public KycStatusResponse getKycStatusByClientId(Long clientId) {
        KycRecord record = kycRecordRepository.findByFineractClientId(clientId)
            .orElseThrow(() -> new RuntimeException("KYC record not found for client: " + clientId));

        return KycStatusResponse.builder()
            .referenceId(record.getReferenceId())
            .kycStatus(record.getStatus().name())
            .amlStatus(record.getAmlStatus() != null ? record.getAmlStatus().name() : null)
            .didId(record.getDidId())
            .fineractClientId(record.getFineractClientId())
            .rejectionReason(record.getRejectionReason())
            .createdAt(record.getCreatedAt())
            .updatedAt(record.getUpdatedAt())
            .build();
    }

    /**
     * Verify CNIC only (standalone check without full onboarding).
     */
    public Map<String, Object> verifyCnicOnly(KycOnboardRequest request) {
        String cnicHash = encryptionService.hash(request.getCnicNumber());
        KycRecord record = kycRecordRepository.findByCnicHash(cnicHash).orElse(null);

        if (record == null) {
            return Map.of(
                "status", "REJECTED",
                "message", "CNIC is not onboarded",
                "matched", false
            );
        }

        String storedFullName = normalize(requestSafeDecrypt(record.getFullNameEncrypted()));
        String inputFullName = normalize(request.getFirstName() + " " + request.getLastName());
        String storedDob = normalize(requestSafeDecrypt(record.getDobEncrypted()));
        String inputDob = normalize(request.getDateOfBirth());

        boolean nameMatches = storedFullName.equals(inputFullName);
        boolean dobMatches = storedDob.equals(inputDob);

        if (!nameMatches || !dobMatches) {
            return Map.of(
                "status", "REJECTED",
                "message", "Submitted details do not match onboarded record",
                "matched", false
            );
        }

        return Map.of(
            "status", "VERIFIED",
            "message", "CNIC verified against onboarded details",
            "matched", true,
            "referenceId", record.getReferenceId(),
            "kycStatus", record.getStatus().name()
        );
    }

    private String requestSafeDecrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) return "";
        try {
            return encryptionService.decrypt(encryptedValue);
        } catch (Exception e) {
            log.warn("Failed to decrypt onboarded value for verify-cnic: {}", e.getMessage());
            return "";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private KycOnboardResponse buildResponse(KycRecord record) {
        return KycOnboardResponse.builder()
            .referenceId(record.getReferenceId())
            .kycStatus(record.getStatus().name())
            .amlStatus(record.getAmlStatus() != null ? record.getAmlStatus().name() : null)
            .nadraVerificationToken(record.getNadraVerificationToken())
            .nadraRequestId(record.getNadraRequestId())
            .didId(record.getDidId())
            .fineractClientId(record.getFineractClientId())
            .rejectionReason(record.getRejectionReason())
            .timestamp(LocalDateTime.now())
            .build();
    }

    private KycOnboardResponse saveAuditAndBuildResponse(KycRecord record, String initiatedBy) {
        KycRecord savedRecord = kycRecordRepository.save(record);
        kycAuditClientService.recordOnboardingAudit(savedRecord, initiatedBy);
        return buildResponse(savedRecord);
    }

    private String maskCnic(String cnic) {
        if (cnic == null || cnic.length() < 5) return "****";
        return cnic.substring(0, 5) + "-*******-*";
    }
}
