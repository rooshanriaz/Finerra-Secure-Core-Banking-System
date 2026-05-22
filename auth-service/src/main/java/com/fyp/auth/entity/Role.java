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
 * Role entity for RBAC.
 * Synced with Apache Fineract roles.
 */
@Entity
@Table(name = "roles", indexes = {
    @Index(name = "idx_role_name", columnList = "name", unique = true),
    @Index(name = "idx_role_fineract_id", columnList = "fineract_role_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Reference to Fineract role ID for sync purposes.
     */
    @Column(name = "fineract_role_id")
    private Long fineractRoleId;

    /**
     * Whether this role is synced from Fineract.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean syncedFromFineract = false;

    /**
     * Whether this role is a system role (cannot be deleted).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean systemRole = false;

    /**
     * Whether this role is enabled.
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
     * Permissions assigned to this role.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    /**
     * Users assigned to this role (inverse side).
     * JsonIgnore to prevent circular reference during serialization.
     */
    @JsonIgnore
    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<User> users = new HashSet<>();

    /**
     * Add a permission to this role.
     */
    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    /**
     * Remove a permission from this role.
     */
    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }

    /**
     * Get role name with ROLE_ prefix for Spring Security.
     */
    public String getAuthority() {
        return "ROLE_" + name.toUpperCase();
    }
}
