package com.fyp.did.contract;

import com.fyp.did.model.DIDDocument;
import com.fyp.did.model.VerifiableCredential;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * DID (Decentralized Identity) Smart Contract for Hyperledger Fabric.
 * 
 * Manages DID Documents and Verifiable Credentials on the blockchain.
 * Used for KYC identity verification in the banking system.
 */
@Contract(
    name = "DIDContract",
    info = @Info(
        title = "DID Contract",
        description = "Smart contract for Decentralized Identity management",
        version = "1.0.0",
        contact = @Contact(
            email = "fyp@giki.edu.pk",
            name = "FYP Team"
        )
    )
)
@Default
public class DIDContract implements ContractInterface {

    private static final Logger LOG = Logger.getLogger(DIDContract.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DID_PREFIX = "DID_";
    private static final String CRED_PREFIX = "CRED_";
    private static final String CNIC_INDEX = "cnicHash~didId";

    /**
     * Initialize the ledger with sample data (optional).
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void InitLedger(final Context ctx) {
        LOG.info("DID Contract initialized successfully");
    }

    /**
     * Create a new DID Document from a verified CNIC hash.
     * 
     * @param ctx         Transaction context
     * @param cnicHash    SHA-256 hash of the CNIC number
     * @param publicKey   Public key for the DID
     * @param controller  Controlling organization
     * @return Created DID Document as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String CreateDID(final Context ctx, final String cnicHash, 
                           final String publicKey, final String controller) {
        ChaincodeStub stub = ctx.getStub();
        String mspId = ctx.getClientIdentity().getMSPID();
        
        LOG.info("Creating DID for CNIC hash: " + cnicHash.substring(0, 8) + "... by " + mspId);

        // Check if DID already exists for this CNIC hash
        String existingDid = findDIDByCnicHash(stub, cnicHash);
        if (existingDid != null) {
            throw new ChaincodeException("DID already exists for this CNIC hash: " + existingDid,
                "DID_ALREADY_EXISTS");
        }

        // Generate DID ID
        String didId = "did:fabric:bank:" + generateShortId(cnicHash);
        String ledgerKey = DID_PREFIX + didId;

        // Create DID Document
        DIDDocument didDoc = new DIDDocument();
        didDoc.setId(didId);
        didDoc.setContext("https://www.w3.org/ns/did/v1");
        didDoc.setCnicHash(cnicHash);
        didDoc.setPublicKey(publicKey);
        didDoc.setController(controller);
        didDoc.setAuthenticationMethod("Ed25519VerificationKey2020");
        didDoc.setStatus("ACTIVE");
        didDoc.setCreatedAt(Instant.now().toString());
        didDoc.setUpdatedAt(Instant.now().toString());
        didDoc.setCreatedBy(mspId);
        didDoc.setCredentials(new ArrayList<>());

        // Store DID Document
        String didJson = didDoc.toJSON();
        stub.putStringState(ledgerKey, didJson);

        // Create composite key index for CNIC hash lookup
        String compositeKey = stub.createCompositeKey(CNIC_INDEX, cnicHash, didId).toString();
        stub.putStringState(compositeKey, didId);

        LOG.info("DID created successfully: " + didId);
        return didJson;
    }

    /**
     * Resolve (look up) a DID Document by its ID.
     * 
     * @param ctx   Transaction context
     * @param didId DID identifier (e.g., "did:fabric:bank:abc123")
     * @return DID Document as JSON
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String ResolveDID(final Context ctx, final String didId) {
        ChaincodeStub stub = ctx.getStub();
        String ledgerKey = DID_PREFIX + didId;

        String didJson = stub.getStringState(ledgerKey);
        if (didJson == null || didJson.isEmpty()) {
            throw new ChaincodeException("DID not found: " + didId, "DID_NOT_FOUND");
        }

        LOG.info("DID resolved: " + didId);
        return didJson;
    }

    /**
     * Look up a DID by CNIC hash.
     * 
     * @param ctx      Transaction context
     * @param cnicHash SHA-256 hash of the CNIC number
     * @return DID Document as JSON
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String ResolveDIDByCnicHash(final Context ctx, final String cnicHash) {
        ChaincodeStub stub = ctx.getStub();
        
        String didId = findDIDByCnicHash(stub, cnicHash);
        if (didId == null) {
            throw new ChaincodeException("No DID found for CNIC hash", "DID_NOT_FOUND");
        }

        return ResolveDID(ctx, didId);
    }

    /**
     * Issue a Verifiable Credential for a DID (e.g., KYC verified).
     * 
     * @param ctx            Transaction context
     * @param didId          DID to issue credential for
     * @param credentialType Type of credential (e.g., "KYCCredential")
     * @param claimType      What was verified (e.g., "cnic_verified")
     * @param claimValue     Verification result hash
     * @param expirationDate Credential expiration date
     * @return Issued credential as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String IssueCredential(final Context ctx, final String didId,
                                   final String credentialType, final String claimType,
                                   final String claimValue, final String expirationDate) {
        ChaincodeStub stub = ctx.getStub();
        String mspId = ctx.getClientIdentity().getMSPID();
        String ledgerKey = DID_PREFIX + didId;

        // Verify DID exists and is active
        String didJson = stub.getStringState(ledgerKey);
        if (didJson == null || didJson.isEmpty()) {
            throw new ChaincodeException("DID not found: " + didId, "DID_NOT_FOUND");
        }

        DIDDocument didDoc = DIDDocument.fromJSON(didJson);
        if (!"ACTIVE".equals(didDoc.getStatus())) {
            throw new ChaincodeException("DID is not active: " + didDoc.getStatus(), "DID_NOT_ACTIVE");
        }

        // Create Verifiable Credential
        String credId = "vc:" + UUID.randomUUID().toString().substring(0, 8);
        
        VerifiableCredential credential = new VerifiableCredential();
        credential.setId(credId);
        credential.setType(credentialType);
        credential.setIssuer("did:fabric:bank:issuer");
        credential.setSubject(didId);
        credential.setIssuanceDate(Instant.now().toString());
        credential.setExpirationDate(expirationDate);
        credential.setCredentialStatus("VALID");
        credential.setClaimType(claimType);
        credential.setClaimValue(claimValue);
        credential.setProof(generateProof(credId, mspId));
        credential.setIssuedBy(mspId);

        // Add credential to DID document
        List<VerifiableCredential> credentials = didDoc.getCredentials();
        if (credentials == null) {
            credentials = new ArrayList<>();
        }
        credentials.add(credential);
        didDoc.setCredentials(credentials);
        didDoc.setUpdatedAt(Instant.now().toString());

        // Update DID Document
        stub.putStringState(ledgerKey, didDoc.toJSON());

        // Store credential separately for direct lookup
        String credKey = CRED_PREFIX + credId;
        stub.putStringState(credKey, credential.toJSON());

        LOG.info("Credential issued: " + credId + " for DID: " + didId);
        return credential.toJSON();
    }

    /**
     * Verify a credential's validity.
     * 
     * @param ctx    Transaction context
     * @param credId Credential ID to verify
     * @return Verification result as JSON
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String VerifyCredential(final Context ctx, final String credId) {
        ChaincodeStub stub = ctx.getStub();
        String credKey = CRED_PREFIX + credId;

        String credJson = stub.getStringState(credKey);
        if (credJson == null || credJson.isEmpty()) {
            return GSON.toJson(new VerificationResult(false, "Credential not found", credId));
        }

        VerifiableCredential credential = VerifiableCredential.fromJSON(credJson);
        
        boolean isValid = "VALID".equals(credential.getCredentialStatus());
        String message = isValid ? "Credential is valid" : "Credential status: " + credential.getCredentialStatus();
        
        LOG.info("Credential verification: " + credId + " = " + isValid);
        return GSON.toJson(new VerificationResult(isValid, message, credId));
    }

    /**
     * Revoke a DID (mark as compromised/invalid).
     * 
     * @param ctx    Transaction context
     * @param didId  DID to revoke
     * @param reason Reason for revocation
     * @return Updated DID Document
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String RevokeDID(final Context ctx, final String didId, final String reason) {
        ChaincodeStub stub = ctx.getStub();
        String mspId = ctx.getClientIdentity().getMSPID();
        String ledgerKey = DID_PREFIX + didId;

        String didJson = stub.getStringState(ledgerKey);
        if (didJson == null || didJson.isEmpty()) {
            throw new ChaincodeException("DID not found: " + didId, "DID_NOT_FOUND");
        }

        DIDDocument didDoc = DIDDocument.fromJSON(didJson);
        didDoc.setStatus("REVOKED");
        didDoc.setUpdatedAt(Instant.now().toString());

        // Revoke all associated credentials
        List<VerifiableCredential> credentials = didDoc.getCredentials();
        if (credentials != null) {
            for (VerifiableCredential cred : credentials) {
                cred.setCredentialStatus("REVOKED");
                String credKey = CRED_PREFIX + cred.getId();
                stub.putStringState(credKey, cred.toJSON());
            }
        }

        stub.putStringState(ledgerKey, didDoc.toJSON());
        
        LOG.info("DID revoked: " + didId + " by " + mspId + " reason: " + reason);
        return didDoc.toJSON();
    }

    /**
     * Get all active DIDs (for admin/audit purposes).
     * 
     * @param ctx Transaction context
     * @return List of DID Documents as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllDIDs(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        List<DIDDocument> results = new ArrayList<>();

        QueryResultsIterator<KeyValue> queryResults = stub.getStateByRange(DID_PREFIX, DID_PREFIX + "\uffff");
        
        for (KeyValue result : queryResults) {
            String value = result.getStringValue();
            if (value != null && !value.isEmpty()) {
                try {
                    DIDDocument doc = DIDDocument.fromJSON(value);
                    results.add(doc);
                } catch (Exception e) {
                    LOG.warning("Failed to parse DID document: " + e.getMessage());
                }
            }
        }

        LOG.info("Retrieved " + results.size() + " DID documents");
        return GSON.toJson(results);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private String findDIDByCnicHash(ChaincodeStub stub, String cnicHash) {
        QueryResultsIterator<KeyValue> results = 
            stub.getStateByPartialCompositeKey(CNIC_INDEX, cnicHash);
        
        for (KeyValue result : results) {
            return result.getStringValue();
        }
        return null;
    }

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

    private String generateProof(String credId, String mspId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String proofInput = credId + mspId + Instant.now().toString();
            byte[] hash = digest.digest(proofInput.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "proof-" + UUID.randomUUID();
        }
    }

    /**
     * Simple verification result object.
     */
    private static class VerificationResult {
        private final boolean valid;
        private final String message;
        private final String credentialId;

        VerificationResult(boolean valid, String message, String credentialId) {
            this.valid = valid;
            this.message = message;
            this.credentialId = credentialId;
        }
    }
}
