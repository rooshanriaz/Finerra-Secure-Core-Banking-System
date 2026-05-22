package com.fyp.auth.service;

import com.fyp.auth.config.AbacProperties;
import com.fyp.auth.entity.IpWhitelist;
import com.fyp.auth.exception.ResourceAlreadyExistsException;
import com.fyp.auth.exception.ValidationException;
import com.fyp.auth.repository.IpWhitelistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.math.BigInteger;
import java.time.*;
import java.util.List;

/**
 * Light ABAC (Attribute-Based Access Control) Service.
 * Implements IP whitelist and business hours checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LightAbacService {

    private final AbacProperties abacProperties;
    private final IpWhitelistRepository ipWhitelistRepository;
    private final AuditService auditService;

    /**
     * Check if access is allowed for a user from a given IP.
     */
    public boolean isAccessAllowed(Long userId, String username, String clientIp) {
        if (!abacProperties.isEnabled()) {
            log.debug("ABAC is disabled, allowing access");
            return true;
        }

        // Check IP whitelist
        if (!isIpAllowed(userId, clientIp)) {
            log.warn("Access denied for user {} from IP {}: Not in whitelist", username, clientIp);
            auditService.logIpAccessDenied(userId, username, clientIp);
            return false;
        }

        // Check business hours
        if (!isWithinBusinessHours()) {
            log.warn("Access denied for user {}: Outside business hours", username);
            auditService.logBusinessHoursAccessDenied(userId, username, clientIp);
            return false;
        }

        return true;
    }

    /**
     * Check if an IP address is allowed.
     */
    public boolean isIpAllowed(Long userId, String clientIp) {
        AbacProperties.IpWhitelistConfig config = abacProperties.getIpWhitelist();
        
        if (!config.isEnabled()) {
            log.debug("IP whitelist is disabled");
            return true;
        }

        // Check configuration-based whitelist first
        for (String allowedIp : config.getAllowedIps()) {
            if (isIpMatch(clientIp, allowedIp)) {
                log.debug("IP {} matched config whitelist entry {}", clientIp, allowedIp);
                return true;
            }
        }

        // Check database-based whitelist
        List<IpWhitelist> dbEntries;
        if (userId != null) {
            dbEntries = ipWhitelistRepository.findApplicableToUser(userId);
        } else {
            dbEntries = ipWhitelistRepository.findGlobalEntries();
        }

        for (IpWhitelist entry : dbEntries) {
            if (isIpMatch(clientIp, entry.getIpAddress())) {
                log.debug("IP {} matched database whitelist entry {}", clientIp, entry.getIpAddress());
                return true;
            }
        }

        log.debug("IP {} not found in any whitelist", clientIp);
        return false;
    }

    /**
     * Check if current time is within business hours.
     */
    public boolean isWithinBusinessHours() {
        AbacProperties.BusinessHoursConfig config = abacProperties.getBusinessHours();
        
        if (!config.isEnabled()) {
            log.debug("Business hours check is disabled");
            return true;
        }

        ZoneId timezone = ZoneId.of(config.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(timezone);
        
        // Check day of week (1=Monday, 7=Sunday)
        int dayOfWeek = now.getDayOfWeek().getValue();
        if (!config.getAllowedDays().contains(dayOfWeek)) {
            log.debug("Current day {} is not in allowed days", dayOfWeek);
            return false;
        }

        // Check time of day
        int currentHour = now.getHour();
        if (currentHour < config.getStartHour() || currentHour >= config.getEndHour()) {
            log.debug("Current hour {} is outside business hours ({}-{})",
                    currentHour, config.getStartHour(), config.getEndHour());
            return false;
        }

        return true;
    }

    /**
     * Check if a client IP matches an allowed IP pattern.
     * Supports single IPs and CIDR notation.
     */
    private boolean isIpMatch(String clientIp, String allowedPattern) {
        try {
            // Handle localhost variations
            if (isLocalhost(clientIp) && isLocalhost(allowedPattern)) {
                return true;
            }

            // Handle CIDR notation
            if (allowedPattern.contains("/")) {
                return isIpInCidr(clientIp, allowedPattern);
            }
            
            // Handle range notation: startIp-endIp
            if (allowedPattern.contains("-")) {
                return isIpInRange(clientIp, allowedPattern);
            }

            // Exact match
            return clientIp.equals(allowedPattern);
        } catch (Exception e) {
            log.warn("Error matching IP {} against pattern {}: {}", 
                    clientIp, allowedPattern, e.getMessage());
            return false;
        }
    }

    /**
     * Check if an IP is localhost.
     */
    private boolean isLocalhost(String ip) {
        return "127.0.0.1".equals(ip) 
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip)
                || "localhost".equalsIgnoreCase(ip);
    }

    /**
     * Check if an IP is within a CIDR range.
     */
    private boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }

            InetAddress cidrAddress = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            InetAddress clientAddress = InetAddress.getByName(ip);

            byte[] cidrBytes = cidrAddress.getAddress();
            byte[] clientBytes = clientAddress.getAddress();

            // Different address families (IPv4 vs IPv6)
            if (cidrBytes.length != clientBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            // Check full bytes
            for (int i = 0; i < fullBytes; i++) {
                if (cidrBytes[i] != clientBytes[i]) {
                    return false;
                }
            }

            // Check remaining bits
            if (remainingBits > 0 && fullBytes < cidrBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((cidrBytes[fullBytes] & mask) != (clientBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (UnknownHostException e) {
            log.warn("Invalid IP or CIDR: {} or {}", ip, cidr);
            return false;
        }
    }

    private boolean isIpInRange(String ip, String range) {
        try {
            String[] parts = range.split("-", 2);
            if (parts.length != 2) return false;
            byte[] target = InetAddress.getByName(ip.trim()).getAddress();
            byte[] start = InetAddress.getByName(parts[0].trim()).getAddress();
            byte[] end = InetAddress.getByName(parts[1].trim()).getAddress();
            if (target.length != start.length || target.length != end.length) return false;
            BigInteger t = new BigInteger(1, target);
            BigInteger s = new BigInteger(1, start);
            BigInteger e = new BigInteger(1, end);
            return t.compareTo(s) >= 0 && t.compareTo(e) <= 0;
        } catch (Exception e) {
            log.warn("Invalid IP range {}: {}", range, e.getMessage());
            return false;
        }
    }

    /**
     * Add an IP to the database whitelist.
     */
    public IpWhitelist addToWhitelist(String ipAddress, String description, 
                                       IpWhitelist.IpType type, String createdBy) {
        String normalizedIp = normalizeIp(ipAddress);
        IpWhitelist.IpType effectiveType = type != null ? type : IpWhitelist.IpType.SINGLE;
        validateWhitelistEntry(normalizedIp, effectiveType);
        if (ipWhitelistRepository.existsByIpAddress(normalizedIp)) {
            throw new ResourceAlreadyExistsException("IpWhitelist", "ipAddress", normalizedIp);
        }

        IpWhitelist entry = IpWhitelist.builder()
                .ipAddress(normalizedIp)
                .description(description != null ? description.trim() : null)
                .type(effectiveType)
                .createdBy(createdBy)
                .enabled(true)
                .build();
        
        return ipWhitelistRepository.save(entry);
    }

    /**
     * Remove an IP from the database whitelist.
     */
    public void removeFromWhitelist(Long id) {
        ipWhitelistRepository.deleteById(id);
    }

    /**
     * Get all whitelist entries.
     */
    public List<IpWhitelist> getAllWhitelistEntries() {
        return ipWhitelistRepository.findByEnabledTrue();
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new ValidationException("ipAddress is required");
        }
        return ipAddress.trim();
    }

    private void validateWhitelistEntry(String ipAddress, IpWhitelist.IpType type) {
        switch (type) {
            case CIDR -> {
                if (!ipAddress.contains("/") || !isIpInCidr(ipAddress.split("/")[0], ipAddress)) {
                    throw new ValidationException("Invalid CIDR notation: " + ipAddress);
                }
            }
            case RANGE -> {
                if (!ipAddress.contains("-") || !isIpInRange(ipAddress.split("-")[0], ipAddress)) {
                    throw new ValidationException("Invalid IP range format: " + ipAddress);
                }
            }
            case SINGLE -> {
                try {
                    InetAddress.getByName(ipAddress);
                } catch (Exception e) {
                    throw new ValidationException("Invalid IP address: " + ipAddress);
                }
            }
            default -> throw new ValidationException("Unsupported whitelist type: " + type);
        }
    }
}
