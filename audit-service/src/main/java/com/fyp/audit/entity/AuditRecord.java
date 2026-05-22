package com.fyp.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String auditId;

    @Column(nullable = false, length = 64)
    private String transactionId;

    private Long accountId;

    private Long loanId;

    @Column(nullable = false, length = 20)
    private String transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 30)
    private String transactionDate;

    @Column(length = 100)
    private String fineractTransactionId;

    @Column(length = 100)
    private String initiatedBy;

    @Column(length = 50)
    private String sourceIp;

    /**
     * SHA-256 hash of the transaction data for integrity verification.
     */
    @Column(nullable = false, length = 128)
    private String dataHash;

    /**
     * Previous audit record's hash (blockchain-like chain).
     */
    @Column(length = 128)
    private String previousHash;

    /**
     * Fabric blockchain transaction ID (when Fabric is enabled).
     */
    @Column(length = 128)
    private String fabricTxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockchainStatus blockchainStatus;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    public enum BlockchainStatus {
        LOCAL_ONLY, SUBMITTED, CONFIRMED, FAILED
    }
}
