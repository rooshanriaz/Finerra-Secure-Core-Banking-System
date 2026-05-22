package com.fyp.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * IP Whitelist entity for ABAC.
 * Stores allowed IP addresses or CIDR ranges.
 */
@Entity
@Table(name = "ip_whitelist", indexes = {
    @Index(name = "idx_ip_address", columnList = "ip_address")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class IpWhitelist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * IP address or CIDR range (e.g., "192.168.1.1" or "192.168.0.0/16").
     */
    @Column(name = "ip_address", nullable = false, length = 50)
    private String ipAddress;

    /**
     * Description/label for this entry.
     */
    @Column(length = 255)
    private String description;

    /**
     * Type of entry: SINGLE, RANGE, CIDR.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IpType type = IpType.SINGLE;

    /**
     * Whether this entry is enabled.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Optional: Restrict to specific user (null = applies to all).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Optional: Restrict to specific role (null = applies to all).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    /**
     * Who created this entry.
     */
    @Column(length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * IP entry type enumeration.
     */
    public enum IpType {
        SINGLE,     // Single IP address
        RANGE,      // IP range (start-end)
        CIDR        // CIDR notation (e.g., 192.168.0.0/16)
    }
}
