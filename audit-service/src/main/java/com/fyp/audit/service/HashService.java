package com.fyp.audit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service for computing SHA-256 hashes for transaction data integrity.
 * Creates deterministic hashes from transaction fields.
 */
@Slf4j
@Service
public class HashService {

    /**
     * Compute SHA-256 hash of transaction data.
     * Fields are concatenated in a deterministic order.
     */
    public String computeTransactionHash(String transactionId, Long accountId, Long loanId,
                                          String transactionType, BigDecimal amount,
                                          String transactionDate, String fineractTxnId,
                                          String initiatedBy) {
        StringBuilder sb = new StringBuilder();
        sb.append("txnId=").append(transactionId != null ? transactionId : "");
        sb.append("|accountId=").append(accountId != null ? accountId : "");
        sb.append("|loanId=").append(loanId != null ? loanId : "");
        sb.append("|type=").append(transactionType != null ? transactionType : "");
        // Match JPA/MySQL round-trip: 100 vs 100.0000 must hash the same
        sb.append("|amount=").append(normalizeAmount(amount));
        sb.append("|date=").append(transactionDate != null ? transactionDate : "");
        sb.append("|fineractTxnId=").append(fineractTxnId != null ? fineractTxnId : "");
        sb.append("|initiatedBy=").append(initiatedBy != null ? initiatedBy : "");

        return sha256(sb.toString());
    }

    /**
     * Compute chain hash linking to previous record.
     */
    public String computeChainHash(String currentDataHash, String previousHash) {
        String combined = currentDataHash + "|prev=" + (previousHash != null ? previousHash : "GENESIS");
        return sha256(combined);
    }

    /**
     * Verify a data hash matches expected value.
     */
    public boolean verifyHash(String expectedHash, String transactionId, Long accountId, Long loanId,
                               String transactionType, BigDecimal amount,
                               String transactionDate, String fineractTxnId,
                               String initiatedBy) {
        String computed = computeTransactionHash(transactionId, accountId, loanId,
            transactionType, amount, transactionDate, fineractTxnId, initiatedBy);
        return computed.equals(expectedHash);
    }

    private static String normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String sha256(String input) {
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
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
