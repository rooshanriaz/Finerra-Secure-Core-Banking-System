package com.fyp.kyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String referenceId;   // Unique reference for this KYC request

    @Column(name = "cnic_encrypted")
    private String cnicEncrypted; // AES-256 encrypted CNIC number

    @Column(name = "cnic_hash", nullable = false)
    private String cnicHash;      // SHA-256 hash for lookups

    @Column(name = "full_name_encrypted")
    private String fullNameEncrypted;

    @Column(name = "dob_encrypted")
    private String dobEncrypted;

    @Column(name = "address_encrypted")
    private String addressEncrypted;

    @Column(name = "father_name_encrypted")
    private String fatherNameEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus status;

    @Column(name = "did_id")
    private String didId;          // DID identifier on Fabric

    @Column(name = "fineract_client_id")
    private Long fineractClientId; // Fineract client ID after onboarding

    @Column(name = "nadra_verification_token")
    private String nadraVerificationToken;

    @Column(name = "nadra_request_id")
    private String nadraRequestId;

    @Column(name = "aml_status")
    @Enumerated(EnumType.STRING)
    private AmlStatus amlStatus;

    @Column(name = "aml_check_id")
    private Long amlCheckId;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum KycStatus {
        INITIATED, CNIC_VERIFIED, AML_CLEARED, DID_CREATED, 
        CLIENT_CREATED, COMPLETED, REJECTED, FAILED
    }

    public enum AmlStatus {
        PENDING, CLEARED, FLAGGED, BLOCKED
    }
}
