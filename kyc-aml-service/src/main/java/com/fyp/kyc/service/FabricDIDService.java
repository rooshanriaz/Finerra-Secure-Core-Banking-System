package com.fyp.kyc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.kyc.dto.DidResponse;
import com.fyp.kyc.entity.DidRecord;
import com.fyp.kyc.repository.DidRecordRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.gateway.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Service for DID operations on Hyperledger Fabric.
 *
 * When fabric.enabled=true, connects to a real Fabric network via the Gateway SDK.
 * When fabric.enabled=false (or if the Fabric call fails), falls back to a local
 * mock implementation that simulates blockchain DID operations.
 */
@Slf4j
@Service
public class FabricDIDService {

    private final DidRecordRepository didRecordRepository;
    private final ObjectMapper objectMapper;
    private final boolean fabricEnabled;

    @Value("${fabric.network-config:fabric-network/config/connection-bank.yaml}")
    private String networkConfigPath;

    @Value("${fabric.wallet-path:wallet}")
    private String walletPath;

    @Value("${fabric.channel-name:banking-channel}")
    private String channelName;

    @Value("${fabric.chaincode-name:did-contract}")
    private String chaincodeName;

    @Value("${fabric.msp-id:BankMSP}")
    private String mspId;

    @Value("${fabric.user-name:admin}")
    private String fabricUserName;

    private Gateway gateway;
    private Network network;
    private Contract contract;
    private volatile boolean fabricReady = false;

    private final Map<String, Map<String, Object>> mockDidStore = new HashMap<>();
    private final Map<String, Map<String, Object>> mockCredStore = new HashMap<>();

    public FabricDIDService(DidRecordRepository didRecordRepository,
                            ObjectMapper objectMapper,
                            @Value("${fabric.enabled:false}") boolean fabricEnabled) {
        this.didRecordRepository = didRecordRepository;
        this.objectMapper = objectMapper;
        this.fabricEnabled = fabricEnabled;
        log.info("Fabric DID Service initialized. Fabric enabled: {}", fabricEnabled);
    }

    @PostConstruct
    public void initGateway() {
        if (!fabricEnabled) {
            log.info("Fabric is disabled — using mock DID implementation");
            return;
        }

        try {
            Path walletDir = Paths.get(walletPath);
            Wallet wallet = Wallets.newFileSystemWallet(walletDir);

            Identity identity = wallet.get(fabricUserName);
            if (identity == null) {
                log.error("Identity '{}' not found in wallet at {}. Falling back to mock.",
                        fabricUserName, walletDir.toAbsolutePath());
                return;
            }

            Path connectionProfile = Paths.get(networkConfigPath);
            Gateway.Builder builder = Gateway.createBuilder()
                    .identity(wallet, fabricUserName)
                    .networkConfig(connectionProfile)
                    .discovery(false);

            this.gateway = builder.connect();
            this.network = gateway.getNetwork(channelName);
            this.contract = network.getContract(chaincodeName);
            this.fabricReady = true;

            log.info("Fabric Gateway connected — channel={}, chaincode={}, user={}",
                    channelName, chaincodeName, fabricUserName);

        } catch (Exception e) {
            log.error("Failed to initialize Fabric Gateway: {}. Falling back to mock.", e.getMessage(), e);
            fabricReady = false;
        }
    }

    @PreDestroy
    public void shutdownGateway() {
        if (gateway != null) {
            gateway.close();
            log.info("Fabric Gateway connection closed");
        }
    }

    private boolean useFabric() {
        return fabricEnabled && fabricReady;
    }

    // ========================================================================
    // Public API
    // ========================================================================

    @CircuitBreaker(name = "fabricService", fallbackMethod = "createDIDFallback")
    public DidResponse createDID(String cnicHash, String controller, String kycRefId) {
        log.info("Creating DID for CNIC hash: {}...", cnicHash.substring(0, 8));

        if (useFabric()) {
            return createDIDOnFabric(cnicHash, controller, kycRefId);
        }
        return createDIDMock(cnicHash, controller, kycRefId);
    }

    @CircuitBreaker(name = "fabricService", fallbackMethod = "resolveDIDFallback")
    public DidResponse resolveDID(String didId) {
        log.info("Resolving DID: {}", didId);

        if (useFabric()) {
            return resolveDIDFromFabric(didId);
        }
        return resolveDIDMock(didId);
    }

    public DidResponse.CredentialInfo issueKycCredential(String didId, String claimValue) {
        log.info("Issuing KYC credential for DID: {}", didId);

        if (useFabric()) {
            try {
                return issueCredentialOnFabric(didId, claimValue);
            } catch (Exception e) {
                log.error("Fabric IssueCredential failed, falling back to mock: {}", e.getMessage(), e);
            }
        }
        return issueCredentialMock(didId, claimValue);
    }

    public Map<String, Object> verifyCredential(String credId) {
        log.info("Verifying credential: {}", credId);

        if (useFabric()) {
            try {
                return verifyCredentialOnFabric(credId);
            } catch (Exception e) {
                log.error("Fabric VerifyCredential failed, falling back to mock: {}", e.getMessage(), e);
            }
        }
        return verifyCredentialMock(credId);
    }

    // ========================================================================
    // Fabric Implementation
    // ========================================================================

    private DidResponse createDIDOnFabric(String cnicHash, String controller, String kycRefId) {
        try {
            Optional<DidRecord> existing = didRecordRepository.findByCnicHash(cnicHash);
            if (existing.isPresent()) {
                log.warn("DID already exists for CNIC hash: {}", cnicHash.substring(0, 8));
                return resolveDID(existing.get().getDidId());
            }

            String publicKey = generateMockPublicKey();

            byte[] result = contract.submitTransaction("CreateDID",
                    cnicHash, publicKey, controller);

            String resultJson = new String(result, StandardCharsets.UTF_8);
            log.info("CreateDID Fabric response: {}", resultJson);

            Map<String, Object> fabricDoc = objectMapper.readValue(resultJson,
                    new TypeReference<Map<String, Object>>() {});
            String didId = (String) fabricDoc.getOrDefault("id", "did:fabric:bank:" + generateShortId(cnicHash));

            DidRecord record = DidRecord.builder()
                    .didId(didId)
                    .cnicHash(cnicHash)
                    .kycReferenceId(kycRefId)
                    .status(DidRecord.DidStatus.ACTIVE)
                    .fabricTxId(extractTxId(fabricDoc))
                    .build();
            didRecordRepository.save(record);

            log.info("DID created on Fabric: {}", didId);

            return mapFabricDocToResponse(fabricDoc, didId);

        } catch (ContractException | TimeoutException | InterruptedException e) {
            log.error("Fabric CreateDID transaction failed: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Falling back to mock for createDID");
            return createDIDMock(cnicHash, controller, kycRefId);
        } catch (Exception e) {
            log.error("Unexpected error during Fabric CreateDID: {}", e.getMessage(), e);
            log.warn("Falling back to mock for createDID");
            return createDIDMock(cnicHash, controller, kycRefId);
        }
    }

    private DidResponse resolveDIDFromFabric(String didId) {
        try {
            byte[] result = contract.evaluateTransaction("ResolveDID", didId);
            String resultJson = new String(result, StandardCharsets.UTF_8);
            log.debug("ResolveDID Fabric response: {}", resultJson);

            if (resultJson.isEmpty()) {
                log.warn("DID {} not found on Fabric", didId);
                return null;
            }

            Map<String, Object> fabricDoc = objectMapper.readValue(resultJson,
                    new TypeReference<Map<String, Object>>() {});

            return mapFabricDocToResponse(fabricDoc, didId);

        } catch (ContractException e) {
            log.error("Fabric ResolveDID query failed: {}", e.getMessage(), e);
            log.warn("Falling back to mock for resolveDID");
            return resolveDIDMock(didId);
        } catch (Exception e) {
            log.error("Unexpected error during Fabric ResolveDID: {}", e.getMessage(), e);
            log.warn("Falling back to mock for resolveDID");
            return resolveDIDMock(didId);
        }
    }

    private DidResponse.CredentialInfo issueCredentialOnFabric(String didId, String claimValue)
            throws ContractException, TimeoutException, InterruptedException, com.fasterxml.jackson.core.JsonProcessingException {

        String now = Instant.now().toString();
        String expiry = Instant.now().plusSeconds(365L * 24 * 60 * 60).toString();

        byte[] result = contract.submitTransaction("IssueCredential",
                didId, "KYCCredential", "cnic_kyc_verified", claimValue, expiry);

        String resultJson = new String(result, StandardCharsets.UTF_8);
        log.info("IssueCredential Fabric response: {}", resultJson);

        Map<String, Object> credDoc = objectMapper.readValue(resultJson,
                new TypeReference<Map<String, Object>>() {});
        String credId = (String) credDoc.getOrDefault("id", "vc:" + UUID.randomUUID().toString().substring(0, 8));

        didRecordRepository.findByDidId(didId).ifPresent(record -> {
            record.setCredentialId(credId);
            didRecordRepository.save(record);
        });

        return DidResponse.CredentialInfo.builder()
                .id(credId)
                .type((String) credDoc.getOrDefault("type", "KYCCredential"))
                .status((String) credDoc.getOrDefault("credentialStatus", "VALID"))
                .issuanceDate((String) credDoc.getOrDefault("issuanceDate", now))
                .expirationDate((String) credDoc.getOrDefault("expirationDate", expiry))
                .build();
    }

    private Map<String, Object> verifyCredentialOnFabric(String credId)
            throws ContractException, com.fasterxml.jackson.core.JsonProcessingException {

        byte[] result = contract.evaluateTransaction("VerifyCredential", credId);
        String resultJson = new String(result, StandardCharsets.UTF_8);
        log.debug("VerifyCredential Fabric response: {}", resultJson);

        return objectMapper.readValue(resultJson,
                new TypeReference<Map<String, Object>>() {});
    }

    // ========================================================================
    // Fabric → DTO mapping
    // ========================================================================

    private DidResponse mapFabricDocToResponse(Map<String, Object> doc, String didId) {
        List<DidResponse.CredentialInfo> credInfos = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> creds =
                (List<Map<String, Object>>) doc.getOrDefault("credentials", List.of());
        for (Map<String, Object> c : creds) {
            credInfos.add(DidResponse.CredentialInfo.builder()
                    .id((String) c.get("id"))
                    .type((String) c.get("type"))
                    .status((String) c.get("credentialStatus"))
                    .issuanceDate((String) c.get("issuanceDate"))
                    .expirationDate((String) c.get("expirationDate"))
                    .build());
        }

        return DidResponse.builder()
                .didId(didId)
                .context((String) doc.getOrDefault("@context", "https://www.w3.org/ns/did/v1"))
                .controller((String) doc.get("controller"))
                .status((String) doc.getOrDefault("status", "ACTIVE"))
                .publicKey((String) doc.get("publicKey"))
                .authenticationMethod((String) doc.getOrDefault("authenticationMethod",
                        "Ed25519VerificationKey2020"))
                .createdAt((String) doc.get("createdAt"))
                .updatedAt((String) doc.get("updatedAt"))
                .credentials(credInfos)
                .build();
    }

    private String extractTxId(Map<String, Object> fabricDoc) {
        Object txId = fabricDoc.get("txId");
        return txId != null ? txId.toString()
                : "fabric-tx-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ========================================================================
    // Mock Implementation (when Fabric is not available)
    // ========================================================================

    private DidResponse createDIDMock(String cnicHash, String controller, String kycRefId) {
        Optional<DidRecord> existing = didRecordRepository.findByCnicHash(cnicHash);
        if (existing.isPresent()) {
            log.warn("DID already exists for CNIC hash: {}", cnicHash.substring(0, 8));
            return resolveDIDMock(existing.get().getDidId());
        }

        String didId = "did:fabric:bank:" + generateShortId(cnicHash);
        String now = Instant.now().toString();
        String publicKey = generateMockPublicKey();

        Map<String, Object> didDoc = new LinkedHashMap<>();
        didDoc.put("id", didId);
        didDoc.put("@context", "https://www.w3.org/ns/did/v1");
        didDoc.put("controller", controller);
        didDoc.put("cnicHash", cnicHash);
        didDoc.put("publicKey", publicKey);
        didDoc.put("authenticationMethod", "Ed25519VerificationKey2020");
        didDoc.put("status", "ACTIVE");
        didDoc.put("createdAt", now);
        didDoc.put("updatedAt", now);
        didDoc.put("createdBy", "BankMSP");
        didDoc.put("credentials", new ArrayList<>());

        mockDidStore.put(didId, didDoc);

        DidRecord record = DidRecord.builder()
                .didId(didId)
                .cnicHash(cnicHash)
                .kycReferenceId(kycRefId)
                .status(DidRecord.DidStatus.ACTIVE)
                .fabricTxId("mock-tx-" + UUID.randomUUID().toString().substring(0, 8))
                .build();
        didRecordRepository.save(record);

        log.info("Mock DID created: {}", didId);

        return DidResponse.builder()
                .didId(didId)
                .context("https://www.w3.org/ns/did/v1")
                .controller(controller)
                .status("ACTIVE")
                .publicKey(publicKey)
                .authenticationMethod("Ed25519VerificationKey2020")
                .createdAt(now)
                .updatedAt(now)
                .credentials(new ArrayList<>())
                .build();
    }

    private DidResponse resolveDIDMock(String didId) {
        Map<String, Object> didDoc = mockDidStore.get(didId);
        if (didDoc == null) {
            Optional<DidRecord> record = didRecordRepository.findByDidId(didId);
            if (record.isPresent()) {
                return DidResponse.builder()
                        .didId(record.get().getDidId())
                        .status(record.get().getStatus().name())
                        .createdAt(record.get().getCreatedAt().toString())
                        .build();
            }
            return null;
        }

        List<DidResponse.CredentialInfo> credInfos = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> creds =
                (List<Map<String, Object>>) didDoc.getOrDefault("credentials", List.of());
        for (Map<String, Object> cred : creds) {
            credInfos.add(DidResponse.CredentialInfo.builder()
                    .id((String) cred.get("id"))
                    .type((String) cred.get("type"))
                    .status((String) cred.get("credentialStatus"))
                    .issuanceDate((String) cred.get("issuanceDate"))
                    .expirationDate((String) cred.get("expirationDate"))
                    .build());
        }

        return DidResponse.builder()
                .didId((String) didDoc.get("id"))
                .context((String) didDoc.get("@context"))
                .controller((String) didDoc.get("controller"))
                .status((String) didDoc.get("status"))
                .publicKey((String) didDoc.get("publicKey"))
                .authenticationMethod((String) didDoc.get("authenticationMethod"))
                .createdAt((String) didDoc.get("createdAt"))
                .updatedAt((String) didDoc.get("updatedAt"))
                .credentials(credInfos)
                .build();
    }

    private DidResponse.CredentialInfo issueCredentialMock(String didId, String claimValue) {
        String credId = "vc:" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();
        String expiry = Instant.now().plusSeconds(365L * 24 * 60 * 60).toString();

        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", credId);
        credential.put("type", "KYCCredential");
        credential.put("issuer", "did:fabric:bank:issuer");
        credential.put("subject", didId);
        credential.put("issuanceDate", now);
        credential.put("expirationDate", expiry);
        credential.put("credentialStatus", "VALID");
        credential.put("claimType", "cnic_kyc_verified");
        credential.put("claimValue", claimValue);
        credential.put("proof", generateProof(credId));
        credential.put("issuedBy", "BankMSP");

        mockCredStore.put(credId, credential);

        Map<String, Object> didDoc = mockDidStore.get(didId);
        if (didDoc != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> creds =
                    (List<Map<String, Object>>) didDoc.getOrDefault("credentials", new ArrayList<>());
            creds.add(credential);
            didDoc.put("credentials", creds);
            didDoc.put("updatedAt", now);
        }

        didRecordRepository.findByDidId(didId).ifPresent(record -> {
            record.setCredentialId(credId);
            didRecordRepository.save(record);
        });

        log.info("Mock KYC credential issued: {} for DID: {}", credId, didId);

        return DidResponse.CredentialInfo.builder()
                .id(credId)
                .type("KYCCredential")
                .status("VALID")
                .issuanceDate(now)
                .expirationDate(expiry)
                .build();
    }

    private Map<String, Object> verifyCredentialMock(String credId) {
        Map<String, Object> credential = mockCredStore.get(credId);
        if (credential == null) {
            return Map.of("valid", false, "message", "Credential not found", "credentialId", credId);
        }
        boolean isValid = "VALID".equals(credential.get("credentialStatus"));
        return Map.of(
                "valid", isValid,
                "message", isValid ? "Credential is valid"
                        : "Credential status: " + credential.get("credentialStatus"),
                "credentialId", credId,
                "subject", credential.getOrDefault("subject", ""),
                "type", credential.getOrDefault("type", "")
        );
    }

    // ========================================================================
    // Circuit-Breaker Fallbacks
    // ========================================================================

    @SuppressWarnings("unused")
    private DidResponse createDIDFallback(String cnicHash, String controller, String kycRefId, Throwable t) {
        log.error("Circuit-breaker fallback for createDID: {}", t.getMessage());
        return createDIDMock(cnicHash, controller, kycRefId);
    }

    @SuppressWarnings("unused")
    private DidResponse resolveDIDFallback(String didId, Throwable t) {
        log.error("Circuit-breaker fallback for resolveDID: {}", t.getMessage());
        return resolveDIDMock(didId);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String generateShortId(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }

    private String generateMockPublicKey() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            byte[] pubKey = kpg.generateKeyPair().getPublic().getEncoded();
            return Base64.getEncoder().encodeToString(pubKey);
        } catch (Exception e) {
            return "mock-public-key-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private String generateProof(String credId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (credId + Instant.now().toString()).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "mock-proof";
        }
    }
}
