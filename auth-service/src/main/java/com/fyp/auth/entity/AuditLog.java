package com.fyp.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit Log entity for security event tracking.
 * Supports STRIDE threat model - especially Repudiation.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_resource", columnList = "resource_type, resource_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User who performed the action (null for system actions).
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Username for readability.
     */
    @Column(length = 100)
    private String username;

    /**
     * Action performed.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    /**
     * Resource type affected (e.g., "USER", "ROLE", "TOKEN").
     */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    /**
     * Resource ID affected.
     */
    @Column(name = "resource_id")
    private Long resourceId;

    /**
     * Detailed description of the action.
     */
    @Column(length = 1000)
    private String description;

    /**
     * Additional details in JSON format.
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * Client IP address.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Request ID for correlation.
     */
    @Column(name = "request_id", length = 36)
    private String requestId;

    /**
     * Whether the action was successful.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean success = true;

    /**
     * Error message if action failed.
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /**
     * Timestamp of the action.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /**
     * Audit action types.
     */
    public enum AuditAction {
        // Authentication
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        TOKEN_ISSUED,
        TOKEN_REFRESHED,
        TOKEN_REVOKED,
        
        // User Management
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        USER_ENABLED,
        USER_DISABLED,
        USER_LOCKED,
        USER_UNLOCKED,
        PASSWORD_CHANGED,
        
        // Role Management
        ROLE_CREATED,
        ROLE_UPDATED,
        ROLE_DELETED,
        ROLE_ASSIGNED,
        ROLE_UNASSIGNED,
        
        // Permission Management
        PERMISSION_CREATED,
        PERMISSION_UPDATED,
        PERMISSION_DELETED,
        PERMISSION_GRANTED,
        PERMISSION_REVOKED,
        
        // ABAC
        IP_WHITELIST_ADDED,
        IP_WHITELIST_REMOVED,
        ACCESS_DENIED_IP,
        ACCESS_DENIED_HOURS,
        
        // Sync
        FINERACT_SYNC_STARTED,
        FINERACT_SYNC_COMPLETED,
        FINERACT_SYNC_FAILED,
        
        // Security Events
        SUSPICIOUS_ACTIVITY,
        BRUTE_FORCE_DETECTED,
        INVALID_TOKEN_USED
    }
}
