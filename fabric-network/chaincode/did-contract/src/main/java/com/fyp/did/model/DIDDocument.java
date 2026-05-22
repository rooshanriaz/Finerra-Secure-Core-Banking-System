package com.fyp.did.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import java.util.List;
import java.util.Objects;

/**
 * W3C DID Document structure stored on the Fabric ledger.
 * Follows W3C DID Core specification (simplified).
 */
@DataType
public class DIDDocument {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Property
    private String id;                    // e.g., "did:fabric:bank:abc123"

    @Property
    private String context;               // "@context": "https://www.w3.org/ns/did/v1"

    @Property
    private String controller;            // Organization controlling this DID

    @Property
    private String cnicHash;              // SHA-256 hash of CNIC number (privacy)

    @Property
    private String publicKey;             // Public key for verification

    @Property
    private String authenticationMethod;  // Authentication method type

    @Property
    private String status;                // ACTIVE, REVOKED, SUSPENDED

    @Property
    private String createdAt;             // ISO timestamp

    @Property
    private String updatedAt;             // ISO timestamp

    @Property
    private String createdBy;             // MSP ID of creator

    @Property
    private List<VerifiableCredential> credentials; // Associated credentials

    // Default constructor
    public DIDDocument() {
        this.context = "https://www.w3.org/ns/did/v1";
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getController() { return controller; }
    public void setController(String controller) { this.controller = controller; }

    public String getCnicHash() { return cnicHash; }
    public void setCnicHash(String cnicHash) { this.cnicHash = cnicHash; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getAuthenticationMethod() { return authenticationMethod; }
    public void setAuthenticationMethod(String authenticationMethod) { this.authenticationMethod = authenticationMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public List<VerifiableCredential> getCredentials() { return credentials; }
    public void setCredentials(List<VerifiableCredential> credentials) { this.credentials = credentials; }

    public String toJSON() {
        return GSON.toJson(this);
    }

    public static DIDDocument fromJSON(String json) {
        return GSON.fromJson(json, DIDDocument.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DIDDocument that = (DIDDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return toJSON();
    }
}
