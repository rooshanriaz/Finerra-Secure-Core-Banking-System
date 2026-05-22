package com.fyp.audit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Service for computing SHA-256 hashes for audit records.
 * Used to ensure data integrity and chain-linking.
 */
@Slf4j
@Service
public class HashingService {

    /**
     * Compute SHA-256 hash of an audit record's core data fields.
     */
    public String computeAuditHash(String transactionId, Long accountId,
                                     String transactionType, BigDecimal amount,
                                     String currency, String previousHash) {
        String data = String.format("%s|%d|%s|%s|%s|%s",
                transactionId, accountId, transactionType,
                amount.toPlainString(), currency,
                previousHash != null ? previousHash : "GENESIS");

        return sha256(data);
    }

    /**
     * Recompute audit hash and compare with stored value.
     */
    public boolean verifyAuditHash(String storedHash, String transactionId, Long accountId,
                                    String transactionType, BigDecimal amount,
                                    String currency, String previousHash) {
        String computed = computeAuditHash(transactionId, accountId, transactionType, amount, currency, previousHash);
        boolean match = computed.equals(storedHash);
        if (!match) {
            log.warn("Hash mismatch for transaction {}: stored={}, computed={}", transactionId, storedHash, computed);
        }
        return match;
    }

    /**
     * Compute generic SHA-256 hash.
     */
    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to compute SHA-256 hash: {}", e.getMessage());
            throw new RuntimeException("Hash computation failed", e);
        }
    }
}
