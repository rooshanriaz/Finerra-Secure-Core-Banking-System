package com.fyp.kyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "aml_checks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kyc_reference_id", nullable = false)
    private String kycReferenceId;

    @Column(name = "name_searched")
    private String nameSearched;

    @Column(name = "cnic_hash")
    private String cnicHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScreeningResult result;

    @Column(name = "match_score")
    private Double matchScore;

    @Column(name = "matched_entity")
    private String matchedEntity; // Name from sanctions list if matched

    @Column(name = "sanctions_list_name")
    private String sanctionsListName; // e.g., "UN_SANCTIONS", "NACTA_LIST"

    @Column(name = "risk_level")
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "checked_at", updatable = false)
    private LocalDateTime checkedAt;

    public enum ScreeningResult {
        CLEAR, MATCH_FOUND, POTENTIAL_MATCH, ERROR
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
