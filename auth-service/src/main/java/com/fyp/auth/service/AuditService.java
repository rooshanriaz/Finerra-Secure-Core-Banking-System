package com.fyp.auth.service;

import com.fyp.auth.dto.response.SecurityNotificationResponse;
import com.fyp.auth.entity.AuditLog;
import com.fyp.auth.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for audit logging.
 * Supports STRIDE threat model - especially Repudiation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an audit event asynchronously.
     */
    @Async
    @Transactional
    public void logAsync(AuditLog.AuditAction action, Long userId, String username,
                         String resourceType, Long resourceId, String description,
                         String ipAddress, String userAgent, String requestId,
                         boolean success, String errorMessage) {
        log(action, userId, username, resourceType, resourceId, description,
            ipAddress, userAgent, requestId, success, errorMessage, null);
    }

    /**
     * Log an audit event synchronously.
     */
    @Transactional
    public AuditLog log(AuditLog.AuditAction action, Long userId, String username,
                        String resourceType, Long resourceId, String description,
                        String ipAddress, String userAgent, String requestId,
                        boolean success, String errorMessage, String details) {
        
        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .userId(userId)
                .username(username)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestId(requestId)
                .success(success)
                .errorMessage(errorMessage)
                .details(details)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        
        // Also log to application logger for centralized logging
        if (success) {
            log.info("AUDIT: {} by {} ({}) on {}/{} from {} - {}",
                    action, username, userId, resourceType, resourceId, ipAddress, description);
        } else {
            log.warn("AUDIT FAILED: {} by {} ({}) on {}/{} from {} - {} Error: {}",
                    action, username, userId, resourceType, resourceId, ipAddress, description, errorMessage);
        }

        return saved;
    }

    /**
     * Log a login success event.
     */
    public void logLoginSuccess(Long userId, String username, String ipAddress, String userAgent) {
        log(AuditLog.AuditAction.LOGIN_SUCCESS, userId, username,
            "USER", userId, "User logged in successfully",
            ipAddress, userAgent, null, true, null, null);
    }

    /**
     * Log a login failure event.
     */
    public void logLoginFailure(String username, String ipAddress, String userAgent, String reason) {
        log(AuditLog.AuditAction.LOGIN_FAILURE, null, username,
            "USER", null, "Login failed: " + reason,
            ipAddress, userAgent, null, false, reason, null);
    }

    /**
     * Log a token revocation event.
     */
    public void logTokenRevocation(Long userId, String username, String reason,
                                   String ipAddress, String revokedBy) {
        log(AuditLog.AuditAction.TOKEN_REVOKED, userId, username,
            "TOKEN", null, "Token revoked: " + reason,
            ipAddress, null, null, true, null, "Revoked by: " + revokedBy);
    }

    /**
     * Log access denied due to IP restriction.
     */
    public void logIpAccessDenied(Long userId, String username, String ipAddress) {
        log(AuditLog.AuditAction.ACCESS_DENIED_IP, userId, username,
            "ACCESS", null, "Access denied: IP not whitelisted",
            ipAddress, null, null, false, "IP not in whitelist", null);
    }

    /**
     * Log access denied due to business hours.
     */
    public void logBusinessHoursAccessDenied(Long userId, String username, String ipAddress) {
        log(AuditLog.AuditAction.ACCESS_DENIED_HOURS, userId, username,
            "ACCESS", null, "Access denied: Outside business hours",
            ipAddress, null, null, false, "Outside business hours", null);
    }

    /**
     * Log suspicious activity detection.
     */
    public void logSuspiciousActivity(Long userId, String username, String ipAddress,
                                      String description, String details) {
        log(AuditLog.AuditAction.SUSPICIOUS_ACTIVITY, userId, username,
            "SECURITY", null, description,
            ipAddress, null, null, false, description, details);
    }

    /**
     * Get audit logs for a user.
     */
    public Page<AuditLog> getLogsForUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }

    /**
     * Get security events.
     */
    public Page<AuditLog> getSecurityEvents(Pageable pageable) {
        return auditLogRepository.findSecurityEvents(pageable);
    }

    /**
     * Recent lockout / brute-force events for admin in-app notifications.
     */
    public List<SecurityNotificationResponse> getLockoutAdminNotifications(Pageable pageable) {
        return auditLogRepository.findLockoutAdminNotifications(pageable)
                .getContent()
                .stream()
                .map(SecurityNotificationResponse::from)
                .toList();
    }

    /**
     * Get failed actions.
     */
    public Page<AuditLog> getFailedActions(Pageable pageable) {
        return auditLogRepository.findBySuccessFalse(pageable);
    }

    /**
     * Get logs by time range.
     */
    public Page<AuditLog> getLogsByTimeRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findByTimestampBetween(start, end, pageable);
    }

    /**
     * Count recent login failures from an IP.
     */
    public long countRecentLoginFailuresFromIp(String ip, int minutesBack) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        return auditLogRepository.countLoginFailuresFromIp(ip, since);
    }
}
