package com.fyp.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Permission entity for fine-grained access control.
 * Synced with Apache Fineract permissions.
 */
@Entity
@Table(name = "permissions", indexes = {
    @Index(name = "idx_permission_code", columnList = "code", unique = true),
    @Index(name = "idx_permission_grouping", columnList = "`grouping`")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Permission code (e.g., "CREATE_CLIENT", "READ_LOAN").
     */
    @Column(nullable = false, unique = true, length = 100)
    private String code;

    /**
     * Human-readable description.
     */
    @Column(length = 500)
    private String description;

    /**
     * Grouping/category for organization (e.g., "client", "loan", "report").
     */
    @Column(name = "`grouping`", length = 50)
    private String grouping;

    /**
     * Entity type this permission applies to (e.g., "CLIENT", "LOAN").
     */
    @Column(length = 50)
    private String entityName;

    /**
     * Action type (e.g., "CREATE", "READ", "UPDATE", "DELETE").
     */
    @Column(length = 20)
    private String actionName;

    /**
     * Whether this permission is synced from Fineract.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean syncedFromFineract = false;

    /**
     * Whether this permission is enabled.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Roles that have this permission (inverse side).
     * JsonIgnore to prevent circular reference during serialization.
     */
    @JsonIgnore
    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
