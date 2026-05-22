package com.fyp.fraud.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long accountId;

    @Builder.Default
    @Column(precision = 19, scale = 4)
    private BigDecimal avgAmount = BigDecimal.ZERO;

    @Builder.Default
    private Long totalTransactions = 0L;

    private LocalDateTime lastTransactionAt;

    private LocalDateTime accountCreatedAt;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateWithTransaction(BigDecimal amount) {
        BigDecimal total = avgAmount.multiply(BigDecimal.valueOf(totalTransactions));
        totalTransactions++;
        avgAmount = total.add(amount).divide(BigDecimal.valueOf(totalTransactions), 4, java.math.RoundingMode.HALF_UP);
        lastTransactionAt = LocalDateTime.now();
    }
}
