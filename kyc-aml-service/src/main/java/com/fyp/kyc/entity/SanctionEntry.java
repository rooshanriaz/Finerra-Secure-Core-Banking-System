package com.fyp.kyc.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sanctions_list")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "cnic_hash")
    private String cnicHash;       // Optional: hash of known CNIC

    @Column(name = "list_name", nullable = false)
    private String listName;       // e.g., "UN_SANCTIONS", "NACTA", "SBP_LIST"

    @Column(name = "entity_type")
    private String entityType;     // INDIVIDUAL, ORGANIZATION

    @Column(name = "country")
    private String country;

    @Column(name = "reason")
    private String reason;

    @Column(name = "active")
    private boolean active;
}
