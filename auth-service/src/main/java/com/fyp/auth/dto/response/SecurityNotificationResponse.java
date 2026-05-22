package com.fyp.auth.dto.response;

import com.fyp.auth.entity.AuditLog;

import java.time.LocalDateTime;

/**
 * Minimal security event for admin in-app notifications (e.g. account lockout).
 */
public record SecurityNotificationResponse(
        long id,
        String action,
        String message,
        String subjectUsername,
        LocalDateTime timestamp
) {
    public static SecurityNotificationResponse from(AuditLog a) {
        return new SecurityNotificationResponse(
                a.getId(),
                a.getAction() != null ? a.getAction().name() : "UNKNOWN",
                a.getDescription() != null ? a.getDescription() : "",
                a.getUsername(),
                a.getTimestamp()
        );
    }
}
