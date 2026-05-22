package com.fyp.audit.contract;

import com.fyp.audit.model.AuditEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Audit Trail Smart Contract for Hyperledger Fabric.
 * 
 * Records transaction audit hashes on the blockchain for immutable audit trails.
 * Each entry contains a SHA-256 hash of the transaction data and a chain link
 * to the previous entry.
 */
@Contract(
    name = "AuditContract",
    info = @Info(
        title = "Audit Contract",
        description = "Smart contract for blockchain-anchored audit trails",
        version = "1.0.0",
        contact = @Contact(
            email = "fyp@giki.edu.pk",
            name = "FYP Team"
        )
    )
)
@Default
public class AuditContract implements ContractInterface {

    private static final Logger LOG = Logger.getLogger(AuditContract.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String AUDIT_PREFIX = "AUDIT_";

    /**
     * Initialize the ledger.
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void InitLedger(final Context ctx) {
        LOG.info("Audit Contract initialized successfully");
    }

    /**
     * Record a transaction audit entry on the blockchain.
     * 
     * @param ctx              Transaction context
     * @param auditId          Unique audit ID
     * @param transactionId    Original transaction ID
     * @param transactionType  Type of transaction (DEPOSIT, WITHDRAWAL, etc.)
     * @param dataHash         SHA-256 hash of the transaction data
     * @param previousHash     Hash of the previous audit entry (chain link)
     * @param initiatedBy      User who initiated the transaction
     * @return Stored audit entry as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String RecordAudit(final Context ctx, final String auditId,
                               final String transactionId, final String transactionType,
                               final String dataHash, final String previousHash,
                               final String initiatedBy) {
        ChaincodeStub stub = ctx.getStub();
        String mspId = ctx.getClientIdentity().getMSPID();
        String ledgerKey = AUDIT_PREFIX + auditId;

        // Check if audit already exists
        String existing = stub.getStringState(ledgerKey);
        if (existing != null && !existing.isEmpty()) {
            throw new ChaincodeException("Audit entry already exists: " + auditId,
                "AUDIT_ALREADY_EXISTS");
        }

        // Create audit entry
        AuditEntry entry = new AuditEntry();
        entry.setAuditId(auditId);
        entry.setTransactionId(transactionId);
        entry.setTransactionType(transactionType);
        entry.setDataHash(dataHash);
        entry.setPreviousHash(previousHash);
        entry.setInitiatedBy(initiatedBy);
        entry.setRecordedBy(mspId);
        entry.setRecordedAt(Instant.now().toString());
        entry.setStatus("CONFIRMED");

        // Store on ledger
        String entryJson = entry.toJSON();
        stub.putStringState(ledgerKey, entryJson);

        LOG.info("Audit entry recorded: " + auditId + " for transaction: " + transactionId);
        return entryJson;
    }

    /**
     * Retrieve an audit entry by ID.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAudit(final Context ctx, final String auditId) {
        ChaincodeStub stub = ctx.getStub();
        String ledgerKey = AUDIT_PREFIX + auditId;

        String entryJson = stub.getStringState(ledgerKey);
        if (entryJson == null || entryJson.isEmpty()) {
            throw new ChaincodeException("Audit entry not found: " + auditId, "AUDIT_NOT_FOUND");
        }

        LOG.info("Audit entry retrieved: " + auditId);
        return entryJson;
    }

    /**
     * Verify audit integrity by checking data hash.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String VerifyAudit(final Context ctx, final String auditId, final String expectedHash) {
        ChaincodeStub stub = ctx.getStub();
        String ledgerKey = AUDIT_PREFIX + auditId;

        String entryJson = stub.getStringState(ledgerKey);
        if (entryJson == null || entryJson.isEmpty()) {
            return GSON.toJson(new VerificationResult(false, "Audit entry not found", auditId));
        }

        AuditEntry entry = AuditEntry.fromJSON(entryJson);
        boolean hashMatch = entry.getDataHash().equals(expectedHash);

        return GSON.toJson(new VerificationResult(
            hashMatch,
            hashMatch ? "Integrity verified" : "INTEGRITY VIOLATION: Hash mismatch!",
            auditId
        ));
    }

    /**
     * Get all audit entries.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllAudits(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        List<AuditEntry> results = new ArrayList<>();

        QueryResultsIterator<KeyValue> queryResults = 
            stub.getStateByRange(AUDIT_PREFIX, AUDIT_PREFIX + "\uffff");
        
        for (KeyValue result : queryResults) {
            String value = result.getStringValue();
            if (value != null && !value.isEmpty()) {
                try {
                    AuditEntry entry = AuditEntry.fromJSON(value);
                    results.add(entry);
                } catch (Exception e) {
                    LOG.warning("Failed to parse audit entry: " + e.getMessage());
                }
            }
        }

        LOG.info("Retrieved " + results.size() + " audit entries");
        return GSON.toJson(results);
    }

    /**
     * Verification result.
     */
    private static class VerificationResult {
        private final boolean valid;
        private final String message;
        private final String auditId;

        VerificationResult(boolean valid, String message, String auditId) {
            this.valid = valid;
            this.message = message;
            this.auditId = auditId;
        }
    }
}
