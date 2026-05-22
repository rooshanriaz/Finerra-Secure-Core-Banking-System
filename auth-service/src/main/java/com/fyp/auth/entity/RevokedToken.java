package com.fyp.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Revoked Token entity for backup storage.
 * Primary revocation uses Redis, this is for persistence and audit.
 */
@Entity
@Table(name = "revoked_tokens", indexes = {
    @Index(name = "idx_token_jti", columnList = "jti", unique = true),
    @Index(name = "idx_token_user_id", columnList = "user_id"),
    @Index(name = "idx_token_expires_at", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * JWT ID (unique identifier for the token).
     */
    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    /**
     * Token hash (for verification without storing full token).
     */
    @Column(length = 64)
    private String tokenHash;

    /**
     * User ID who owned this token.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Username for audit purposes.
     */
    @Column(length = 100)
    private String username;

    /**
     * When the token was revoked.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime revokedAt;

    /**
     * When the token would have expired naturally.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Reason for revocation.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RevocationReason reason;

    /**
     * Additional details about revocation.
     */
    @Column(length = 500)
    private String details;

    /**
     * Who revoked the token (admin user or system).
     */
    @Column(length = 100)
    private String revokedBy;

    /**
     * IP address from which revocation was requested.
     */
    @Column(length = 45)
    private String revokedFromIp;

    /**
     * Revocation reason enumeration.
     */
    public enum RevocationReason {
        LOGOUT,             // Normal user logout
        PASSWORD_CHANGE,    // Password was changed
        ACCOUNT_DISABLED,   // Account was disabled
        ROLE_CHANGE,        // User's roles were changed
        SECURITY_CONCERN,   // Security issue detected
        ADMIN_ACTION,       // Admin manually revoked
        SESSION_EXPIRED,    // Session timeout
        SUSPICIOUS_ACTIVITY // Detected suspicious activity
    }
}
