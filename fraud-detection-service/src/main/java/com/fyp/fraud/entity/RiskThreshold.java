package com.fyp.fraud.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_thresholds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String thresholdName;

    @Column(name = "threshold_value", nullable = false)
    private Double value;

    @Column(length = 200)
    private String description;

    @Column(length = 100)
    private String updatedBy;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
