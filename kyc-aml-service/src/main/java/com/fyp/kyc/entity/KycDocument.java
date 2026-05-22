package com.fyp.kyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kyc_reference_id", nullable = false)
    private String kycReferenceId;

    @Column(name = "document_type", nullable = false)
    private String documentType;    // CNIC_FRONT, CNIC_BACK, PHOTO, PROOF_OF_ADDRESS

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "storage_path")
    private String storagePath;     // File system path or object storage key

    @Column(name = "hash")
    private String hash;            // SHA-256 hash of the file for integrity

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    public enum DocumentStatus {
        UPLOADED, VERIFIED, REJECTED
    }
}
