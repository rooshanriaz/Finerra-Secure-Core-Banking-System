package com.fyp.did.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import java.util.Objects;

/**
 * W3C Verifiable Credential structure.
 * Represents a KYC verification credential issued after successful identity check.
 */
@DataType
public class VerifiableCredential {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Property
    private String id;                    // Credential ID

    @Property
    private String type;                  // e.g., "KYCCredential", "IdentityCredential"

    @Property
    private String issuer;                // DID of the issuer (bank)

    @Property
    private String subject;               // DID of the subject (customer)

    @Property
    private String issuanceDate;          // When credential was issued

    @Property
    private String expirationDate;        // When credential expires

    @Property
    private String credentialStatus;      // VALID, REVOKED, EXPIRED

    @Property
    private String claimType;             // What was verified (e.g., "cnic_verified")

    @Property
    private String claimValue;            // Verification result hash

    @Property
    private String proof;                 // Digital signature proof

    @Property
    private String issuedBy;              // MSP ID of issuer

    // Default constructor
    public VerifiableCredential() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getIssuanceDate() { return issuanceDate; }
    public void setIssuanceDate(String issuanceDate) { this.issuanceDate = issuanceDate; }

    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }

    public String getCredentialStatus() { return credentialStatus; }
    public void setCredentialStatus(String credentialStatus) { this.credentialStatus = credentialStatus; }

    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }

    public String getClaimValue() { return claimValue; }
    public void setClaimValue(String claimValue) { this.claimValue = claimValue; }

    public String getProof() { return proof; }
    public void setProof(String proof) { this.proof = proof; }

    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }

    public String toJSON() {
        return GSON.toJson(this);
    }

    public static VerifiableCredential fromJSON(String json) {
        return GSON.fromJson(json, VerifiableCredential.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerifiableCredential that = (VerifiableCredential) o;
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
