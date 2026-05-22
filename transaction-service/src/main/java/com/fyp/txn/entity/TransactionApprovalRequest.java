package com.fyp.txn.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_approval_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String requestId;

    private Long accountId;
    private Long loanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RequestedTransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String transactionDate;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, length = 100)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status;

    @Column(length = 100)
    private String decidedBy;

    @Column(length = 500)
    private String decisionNote;

    private LocalDateTime decidedAt;

    @Column(length = 64)
    private String executedTransactionId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum RequestedTransactionType {
        DEPOSIT, WITHDRAWAL, LOAN_REPAYMENT
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, DECLINED, EXECUTED, EXECUTION_FAILED
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

