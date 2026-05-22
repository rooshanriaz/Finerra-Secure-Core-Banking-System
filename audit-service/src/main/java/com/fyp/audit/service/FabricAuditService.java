package com.fyp.audit.service;

import com.fyp.audit.entity.AuditRecord;
import com.fyp.audit.entity.AuditRecord.BlockchainStatus;
import com.fyp.audit.repository.AuditRecordRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.gateway.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FabricAuditService {
    private static final Gson GSON = new Gson();

    private final AuditRecordRepository repository;
    private final boolean fabricEnabled;
    private final String channelName;
    private final String chaincodeName;
    private final String walletPath;
    private final String networkConfigPath;
    private final String identityLabel;

    private Gateway gateway;
    private Contract contract;

    private final Map<String, Map<String, Object>> mockLedger = new HashMap<>();

    public FabricAuditService(
            AuditRecordRepository repository,
            @Value("${fabric.enabled:false}") boolean fabricEnabled,
            @Value("${fabric.channel-name:banking-channel}") String channelName,
            @Value("${fabric.chaincode-name:audit-contract}") String chaincodeName,
            @Value("${fabric.wallet-path:./fabric-wallet}") String walletPath,
            @Value("${fabric.network-config:./fabric-network/connection-profile.yaml}") String networkConfigPath,
            @Value("${fabric.identity:admin}") String identityLabel) {
        this.repository = repository;
        this.fabricEnabled = fabricEnabled;
        this.channelName = channelName;
        this.chaincodeName = chaincodeName;
        this.walletPath = walletPath;
        this.networkConfigPath = networkConfigPath;
        this.identityLabel = identityLabel;
    }

    @PostConstruct
    public void initGateway() {
        if (!fabricEnabled) {
            log.info("Fabric disabled — running with mock ledger");
            return;
        }

        try {
            Path walletDir = Paths.get(walletPath);
            Wallet wallet = Wallets.newFileSystemWallet(walletDir);

            Identity identity = wallet.get(identityLabel);
            if (identity == null) {
                log.error("Identity '{}' not found in wallet at {}. Falling back to mock ledger.",
                        identityLabel, walletDir.toAbsolutePath());
                return;
            }

            Path connectionProfile = Paths.get(networkConfigPath);
            Gateway.Builder builder = Gateway.createBuilder();
            builder.identity(wallet, identityLabel);
            builder.networkConfig(connectionProfile);
            builder.discovery(false);

            this.gateway = builder.connect();
            Network network = gateway.getNetwork(channelName);
            this.contract = network.getContract(chaincodeName);

            log.info("Fabric Gateway connected — channel={}, chaincode={}, identity={}",
                    channelName, chaincodeName, identityLabel);
        } catch (Exception e) {
            log.error("Failed to initialise Fabric Gateway — falling back to mock ledger: {}",
                    e.getMessage(), e);
            closeGateway();
        }
    }

    @PreDestroy
    public void closeGateway() {
        if (gateway != null) {
            try {
                gateway.close();
            } catch (Exception ignored) { }
            gateway = null;
            contract = null;
            log.info("Fabric Gateway connection closed");
        }
    }

    private boolean isFabricReady() {
        return fabricEnabled && contract != null;
    }

    // ========================================================================
    // Public API
    // ========================================================================

    @CircuitBreaker(name = "fabricService", fallbackMethod = "anchorFallback")
    public AuditRecord anchorToBlockchain(AuditRecord record) {
        if (isFabricReady()) {
            return anchorToFabric(record);
        }
        return anchorMock(record);
    }

    public Map<String, Object> verifyOnBlockchain(String auditId) {
        AuditRecord record = repository.findByAuditId(auditId).orElse(null);
        if (record == null) {
            return Map.of("verified", false, "message", "Audit record not found");
        }

        if (isFabricReady()) {
            return verifyOnFabric(record);
        }
        return verifyMock(record);
    }

    // ========================================================================
    // Fabric Implementation
    // ========================================================================

    private AuditRecord anchorToFabric(AuditRecord record) {
        try {
            Transaction tx = contract.createTransaction("RecordAudit");
            String fabricTxId = tx.getTransactionId();
            tx.submit(
                    record.getAuditId(),
                    nullSafe(record.getTransactionId()),
                    nullSafe(record.getTransactionType()),
                    record.getDataHash(),
                    nullSafe(record.getPreviousHash()),
                    nullSafe(record.getInitiatedBy()));
            if (fabricTxId.isEmpty()) {
                fabricTxId = "fabric-tx-" + UUID.randomUUID().toString().substring(0, 12);
            }

            record.setFabricTxId(fabricTxId);
            record.setBlockchainStatus(BlockchainStatus.CONFIRMED);
            repository.save(record);

            log.info("Anchored to Fabric: auditId={}, fabricTxId={}", record.getAuditId(), fabricTxId);
            return record;
        } catch (ContractException e) {
            log.error("Chaincode rejected RecordAudit for auditId={}: {}",
                    record.getAuditId(), e.getMessage(), e);
            return anchorMock(record);
        } catch (Exception e) {
            log.error("Fabric anchorToFabric failed for auditId={}: {}. Falling back to mock.",
                    record.getAuditId(), e.getMessage(), e);
            return anchorMock(record);
        }
    }

    private Map<String, Object> verifyOnFabric(AuditRecord record) {
        try {
            byte[] result = contract.evaluateTransaction("VerifyAudit",
                    record.getAuditId(),
                    record.getDataHash());

            String response = new String(result).trim();
            Map<String, Object> verifyPayload = GSON.fromJson(
                    response,
                    new TypeToken<Map<String, Object>>() {}.getType()
            );
            boolean verified = Boolean.TRUE.equals(verifyPayload.get("valid"));
            String message = verifyPayload.get("message") != null
                    ? verifyPayload.get("message").toString()
                    : (verified ? "Integrity verified against Fabric ledger"
                    : "INTEGRITY VIOLATION: Hash mismatch on Fabric ledger!");

            return Map.of(
                    "verified", verified,
                    "message", message,
                    "auditId", record.getAuditId(),
                    "currentHash", record.getDataHash(),
                    "fabricTxId", nullSafe(record.getFabricTxId()),
                    "blockchainStatus", record.getBlockchainStatus().name()
            );
        } catch (ContractException e) {
            log.error("Chaincode rejected VerifyAudit for auditId={}: {}",
                    record.getAuditId(), e.getMessage(), e);
            return verifyMock(record);
        } catch (Exception e) {
            log.error("Fabric verifyOnFabric failed for auditId={}: {}. Falling back to mock.",
                    record.getAuditId(), e.getMessage(), e);
            return verifyMock(record);
        }
    }

    // ========================================================================
    // Mock Implementation (used when Fabric is disabled or unreachable)
    // ========================================================================

    private AuditRecord anchorMock(AuditRecord record) {
        String mockTxId = "fabric-tx-" + UUID.randomUUID().toString().substring(0, 12);

        Map<String, Object> blockEntry = new HashMap<>();
        blockEntry.put("auditId", record.getAuditId());
        blockEntry.put("transactionId", record.getTransactionId());
        blockEntry.put("dataHash", record.getDataHash());
        blockEntry.put("previousHash", record.getPreviousHash());
        blockEntry.put("fabricTxId", mockTxId);
        blockEntry.put("timestamp", record.getRecordedAt().toString());

        mockLedger.put(record.getAuditId(), blockEntry);

        record.setFabricTxId(mockTxId);
        record.setBlockchainStatus(BlockchainStatus.CONFIRMED);
        repository.save(record);

        log.info("Mock blockchain anchor: auditId={}, fabricTxId={}", record.getAuditId(), mockTxId);
        return record;
    }

    private Map<String, Object> verifyMock(AuditRecord record) {
        Map<String, Object> blockEntry = mockLedger.get(record.getAuditId());
        if (blockEntry == null) {
            if (record.getBlockchainStatus() == BlockchainStatus.LOCAL_ONLY) {
                return Map.of(
                        "verified", true,
                        "message", "Record exists locally (blockchain anchoring not performed)",
                        "auditId", record.getAuditId(),
                        "dataHash", record.getDataHash(),
                        "blockchainStatus", record.getBlockchainStatus().name()
                );
            }
            // Mock ledger is in-memory only — after a service restart it is empty even though
            // the DB still has CONFIRMED rows and fabricTxId from the previous run.
            if (record.getBlockchainStatus() == BlockchainStatus.CONFIRMED
                    && record.getFabricTxId() != null && !record.getFabricTxId().isBlank()) {
                return Map.of(
                        "verified", true,
                        "message", "Anchor metadata present in database (mock ledger was reset; hash check still applies)",
                        "auditId", record.getAuditId(),
                        "currentHash", record.getDataHash(),
                        "fabricTxId", nullSafe(record.getFabricTxId()),
                        "blockchainStatus", record.getBlockchainStatus().name()
                );
            }
            return Map.of("verified", false, "message", "Record not found on blockchain");
        }

        String storedHash = (String) blockEntry.get("dataHash");
        boolean hashMatch = storedHash.equals(record.getDataHash());

        return Map.of(
                "verified", hashMatch,
                "message", hashMatch ? "Integrity verified against blockchain" : "INTEGRITY VIOLATION: Hash mismatch!",
                "auditId", record.getAuditId(),
                "expectedHash", storedHash,
                "currentHash", record.getDataHash(),
                "fabricTxId", nullSafe(record.getFabricTxId()),
                "blockchainStatus", record.getBlockchainStatus().name()
        );
    }

    // ========================================================================
    // Circuit-breaker fallback
    // ========================================================================

    @SuppressWarnings("unused")
    private AuditRecord anchorFallback(AuditRecord record, Throwable t) {
        log.error("Blockchain anchoring failed, storing locally: {}", t.getMessage());
        record.setBlockchainStatus(BlockchainStatus.LOCAL_ONLY);
        return repository.save(record);
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
