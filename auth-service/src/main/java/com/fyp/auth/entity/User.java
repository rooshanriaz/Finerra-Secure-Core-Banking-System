package com.fyp.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity for authentication and authorization.
 * Supports both local authentication and future DID integration (Phase 4).
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_fineract_id", columnList = "fineract_user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, length = 150)
    private String email;

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    /**
     * Reference to Fineract user ID for sync purposes.
     */
    @Column(name = "fineract_user_id")
    private Long fineractUserId;

    /**
     * Decentralized Identifier linked via KYC onboarding.
     */
    @Column(name = "did_identifier", length = 255)
    private String didIdentifier;

    /**
     * Whether TOTP-based MFA is enabled for this user.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;

    /**
     * Encrypted TOTP secret for MFA.
     */
    @Column(name = "mfa_secret", length = 512)
    private String mfaSecret;

    /**
     * Timestamp of the last successful MFA verification.
     */
    @Column(name = "mfa_verified_at")
    private LocalDateTime mfaVerifiedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean accountNonExpired = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean accountNonLocked = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean credentialsNonExpired = true;

    /**
     * Last login timestamp for audit purposes.
     */
    private LocalDateTime lastLoginAt;

    /**
     * Last login IP address.
     */
    @Column(length = 45)
    private String lastLoginIp;

    /**
     * Number of failed login attempts (for lockout).
     */
    @Builder.Default
    private int failedLoginAttempts = 0;

    /**
     * When the account was locked (if applicable).
     */
    private LocalDateTime lockedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * User's assigned roles.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /**
     * Add a role to the user.
     */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Remove a role from the user.
     */
    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    /**
     * Get full name.
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return username;
    }

    /**
     * Check if user is active (enabled and not locked).
     */
    public boolean isActive() {
        return enabled && accountNonLocked && accountNonExpired && credentialsNonExpired;
    }
}
