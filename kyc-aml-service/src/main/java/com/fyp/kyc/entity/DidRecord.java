package com.fyp.kyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "did_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DidRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "did_id", nullable = false, unique = true)
    private String didId;         // e.g., "did:fabric:bank:abc123"

    @Column(name = "cnic_hash", nullable = false)
    private String cnicHash;

    @Column(name = "kyc_reference_id")
    private String kycReferenceId;

    @Column(name = "fineract_client_id")
    private Long fineractClientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DidStatus status;

    @Column(name = "credential_id")
    private String credentialId;

    @Column(name = "fabric_tx_id")
    private String fabricTxId;   // Fabric transaction ID

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DidStatus {
        ACTIVE, REVOKED, SUSPENDED
    }
}
