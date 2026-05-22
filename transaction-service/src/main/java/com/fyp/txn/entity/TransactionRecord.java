package com.fyp.txn.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String transactionId;

    private Long accountId;

    private Long loanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 30)
    private String transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 100)
    private String fineractTransactionId;

    @Column(length = 64)
    private String auditReference;

    @Column(length = 20)
    private String auditStatus;

    @Column(length = 100)
    private String initiatedBy;

    @Column(length = 50)
    private String sourceIp;

    @Column(length = 500)
    private String note;

    @Column(precision = 5, scale = 4)
    private BigDecimal riskScore;

    @Column(length = 20)
    private String riskLevel;

    @Column(length = 64)
    private String fraudAlertId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, LOAN_REPAYMENT, TRANSFER
    }

    public enum TransactionStatus {
        PENDING, VALIDATED, PROCESSING, COMPLETED, FAILED, REJECTED, FRAUD_BLOCKED
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
