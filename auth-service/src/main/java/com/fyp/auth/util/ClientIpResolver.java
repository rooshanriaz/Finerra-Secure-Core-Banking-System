package com.fyp.auth.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves client IP with conservative proxy-header handling.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // Trust forwarding headers only when request came through a local/private proxy.
        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }

        return remoteAddr;
    }

    private static boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        return "127.0.0.1".equals(ip)
            || "::1".equals(ip)
            || "0:0:0:0:0:0:0:1".equals(ip)
            || ip.startsWith("10.")
            || ip.startsWith("192.168.")
            || ip.startsWith("172.16.")
            || ip.startsWith("172.17.")
            || ip.startsWith("172.18.")
            || ip.startsWith("172.19.")
            || ip.startsWith("172.2")
            || ip.startsWith("172.30.")
            || ip.startsWith("172.31.");
    }
}

