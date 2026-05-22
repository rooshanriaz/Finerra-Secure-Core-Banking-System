package com.fyp.audit.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Audit Entry model for blockchain storage.
 * Records a SHA-256 hash of transaction data with chain linking.
 */
public class AuditEntry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String auditId;
    private String transactionId;
    private String transactionType;
    private String dataHash;
    private String previousHash;
    private String initiatedBy;
    private String recordedBy;
    private String recordedAt;
    private String status;

    // Getters and Setters

    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }

    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public String getRecordedAt() { return recordedAt; }
    public void setRecordedAt(String recordedAt) { this.recordedAt = recordedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String toJSON() {
        return GSON.toJson(this);
    }

    public static AuditEntry fromJSON(String json) {
        return GSON.fromJson(json, AuditEntry.class);
    }
}
