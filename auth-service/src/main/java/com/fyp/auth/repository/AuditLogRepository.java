package com.fyp.auth.repository;

import com.fyp.auth.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Audit Log entity operations.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find by user ID with pagination.
     */
    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    /**
     * Find by action with pagination.
     */
    Page<AuditLog> findByAction(AuditLog.AuditAction action, Pageable pageable);

    /**
     * Find by resource type and ID.
     */
    List<AuditLog> findByResourceTypeAndResourceId(String resourceType, Long resourceId);

    /**
     * Find by timestamp range with pagination.
     */
    Page<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find by IP address.
     */
    List<AuditLog> findByIpAddress(String ipAddress);

    /**
     * Find failed actions.
     */
    Page<AuditLog> findBySuccessFalse(Pageable pageable);

    /**
     * Find security events (suspicious activity, brute force, etc.).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action IN (" +
           "'SUSPICIOUS_ACTIVITY', 'BRUTE_FORCE_DETECTED', 'INVALID_TOKEN_USED', " +
           "'ACCESS_DENIED_IP', 'ACCESS_DENIED_HOURS', 'LOGIN_FAILURE') " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findSecurityEvents(Pageable pageable);

    /**
     * Admin dashboard: account lockouts and IP-level brute-force signals.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action = 'USER_LOCKED' OR " +
           "(a.action = 'SUSPICIOUS_ACTIVITY' AND LOWER(a.description) LIKE '%brute force%') " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findLockoutAdminNotifications(Pageable pageable);

    /**
     * Find recent login failures for a user.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.action = 'LOGIN_FAILURE' " +
           "AND a.timestamp > :since " +
           "ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentLoginFailures(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Count login failures from an IP in a time window.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.ipAddress = :ip " +
           "AND a.action = 'LOGIN_FAILURE' " +
           "AND a.timestamp > :since")
    long countLoginFailuresFromIp(@Param("ip") String ip, @Param("since") LocalDateTime since);

    /**
     * Find by request ID.
     */
    List<AuditLog> findByRequestId(String requestId);
}
