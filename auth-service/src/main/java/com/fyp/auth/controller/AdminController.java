package com.fyp.auth.controller;

import com.fyp.auth.dto.response.ApiResponse;
import com.fyp.auth.dto.response.SecurityNotificationResponse;
import com.fyp.auth.dto.request.IpWhitelistRequest;
import com.fyp.auth.entity.AuditLog;
import com.fyp.auth.entity.IpWhitelist;
import com.fyp.auth.service.AuditService;
import com.fyp.auth.service.LightAbacService;
import com.fyp.auth.service.RevocationService;
import com.fyp.auth.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for system management.
 * Handles IP whitelist, audit logs, and revocation management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final LightAbacService lightAbacService;
    private final AuditService auditService;
    private final RevocationService revocationService;

    // ========================
    // IP Whitelist Management
    // ========================

    /**
     * Get all IP whitelist entries.
     * GET /api/v1/admin/ip-whitelist
     */
    @GetMapping("/ip-whitelist")
    public ResponseEntity<ApiResponse<List<IpWhitelist>>> getIpWhitelist() {
        List<IpWhitelist> entries = lightAbacService.getAllWhitelistEntries();
        return ResponseEntity.ok(ApiResponse.success(entries));
    }

    /**
     * Add IP to whitelist.
     * POST /api/v1/admin/ip-whitelist
     */
    @PostMapping("/ip-whitelist")
    public ResponseEntity<ApiResponse<IpWhitelist>> addToWhitelist(
            @RequestBody(required = false) IpWhitelistRequest body,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) IpWhitelist.IpType type,
            Authentication authentication) {

        String effectiveIp =
            body != null && body.getIpAddress() != null && !body.getIpAddress().isBlank() ? body.getIpAddress()
                : (body != null && body.getIp() != null && !body.getIp().isBlank() ? body.getIp()
                : (ipAddress != null && !ipAddress.isBlank() ? ipAddress : ip));
        String effectiveDescription =
            body != null && body.getDescription() != null ? body.getDescription() : description;
        IpWhitelist.IpType effectiveType =
            body != null && body.getType() != null ? body.getType() : (type != null ? type : IpWhitelist.IpType.SINGLE);
        String createdBy = authentication != null ? authentication.getName() : "admin";

        IpWhitelist entry = lightAbacService.addToWhitelist(effectiveIp, effectiveDescription, effectiveType, createdBy);
        return ResponseEntity.ok(ApiResponse.success("IP added to whitelist", entry));
    }

    /**
     * Remove IP from whitelist.
     * DELETE /api/v1/admin/ip-whitelist/{id}
     */
    @DeleteMapping("/ip-whitelist/{id}")
    public ResponseEntity<ApiResponse<Void>> removeFromWhitelist(@PathVariable Long id) {
        lightAbacService.removeFromWhitelist(id);
        return ResponseEntity.ok(ApiResponse.success("IP removed from whitelist"));
    }

    /**
     * Check if an IP is allowed.
     * GET /api/v1/admin/ip-whitelist/check
     */
    @GetMapping("/ip-whitelist/check")
    public ResponseEntity<ApiResponse<Boolean>> checkIp(
            @RequestParam String ipAddress,
            @RequestParam(required = false) Long userId) {
        boolean allowed = lightAbacService.isIpAllowed(userId, ipAddress);
        return ResponseEntity.ok(ApiResponse.success("IP check result", allowed));
    }

    // ========================
    // Audit Logs
    // ========================

    /**
     * Get security events.
     * GET /api/v1/admin/audit/security
     */
    @GetMapping("/audit/security")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getSecurityEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<AuditLog> events = auditService.getSecurityEvents(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    /**
     * Get failed actions.
     * GET /api/v1/admin/audit/failed
     */
    @GetMapping("/audit/failed")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getFailedActions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<AuditLog> events = auditService.getFailedActions(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    /**
     * Get audit logs for a user.
     * GET /api/v1/admin/audit/user/{userId}
     */
    @GetMapping("/audit/user/{userId}")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getUserAuditLogs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<AuditLog> logs = auditService.getLogsForUser(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * In-app security notifications for administrators (lockouts, brute-force at IP).
     * GET /api/v1/admin/notifications/security-lockouts
     */
    @GetMapping("/notifications/security-lockouts")
    public ResponseEntity<ApiResponse<List<SecurityNotificationResponse>>> getSecurityLockoutNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        List<SecurityNotificationResponse> items = auditService.getLockoutAdminNotifications(
                PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "timestamp")));
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    // ========================
    // Token Revocation
    // ========================

    /**
     * Get revocation statistics.
     * GET /api/v1/admin/revocation/stats
     */
    @GetMapping("/revocation/stats")
    public ResponseEntity<ApiResponse<RevocationService.RevocationStats>> getRevocationStats() {
        RevocationService.RevocationStats stats = revocationService.getStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // ========================
    // System Info
    // ========================

    /**
     * Get system health info.
     * GET /api/v1/admin/system/info
     */
    @GetMapping("/system/info")
    public ResponseEntity<ApiResponse<SystemInfo>> getSystemInfo(HttpServletRequest request) {
        SystemInfo info = new SystemInfo(
                "auth-service",
                "1.0.0",
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().freeMemory(),
                Runtime.getRuntime().totalMemory(),
                System.getProperty("java.version"),
                getClientIp(request)
        );
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }

    /**
     * System info record.
     */
    public record SystemInfo(
            String name,
            String version,
            int processors,
            long freeMemory,
            long totalMemory,
            String javaVersion,
            String clientIp
    ) {}
}
