package com.fyp.fraud.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String alertId;

    @Column(nullable = false, length = 64)
    private String transactionId;

    private Long accountId;

    @Column(precision = 5, scale = 4)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(length = 500)
    private String riskFactors;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(length = 20)
    private String recommendation;

    @Column(precision = 19, scale = 4)
    private BigDecimal transactionAmount;

    @Column(length = 20)
    private String transactionType;

    @Column(length = 100)
    private String initiatedBy;

    @Column(length = 100)
    private String resolvedBy;

    @Column(length = 500)
    private String resolutionNote;

    private LocalDateTime resolvedAt;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlertStatus {
        OPEN, INVESTIGATING, RESOLVED, FALSE_POSITIVE
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
