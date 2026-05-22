package com.fyp.auth.config;

import com.fyp.auth.service.TokenService;
import com.fyp.auth.repository.UserRepository;
import com.fyp.auth.service.LightAbacService;
import com.fyp.auth.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JWT Authentication Filter.
 * Extracts the JWT from the Authorization header, validates it,
 * and sets up the Spring Security authentication context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final JwtDecoder keycloakJwtDecoder;
    private final LightAbacService lightAbacService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractToken(request);
            String method = request.getMethod();
            String uri = request.getRequestURI();

            if (token == null) {
                log.info("JWT Filter: No token found for {} {} | Auth header: {}",
                        method, uri, request.getHeader("Authorization") != null ? "present but malformed" : "missing");
            } else if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.info("JWT Filter: Authentication already set for {} {}", method, uri);
            } else {
                Long userId = null;
                String username = null;
                if (tokenService.validateToken(token)) {
                    username = tokenService.extractUsername(token);
                    try {
                        userId = tokenService.extractUserId(token);
                    } catch (Exception ignored) {
                        // Fall back to lookup by username below.
                    }
                    Set<String> roles = tokenService.extractRoles(token);
                    Set<String> permissions = tokenService.extractPermissions(token);

                    // Build authorities list: ROLE_xxx for roles, permission codes as-is
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                    // Add roles with ROLE_ prefix (required for hasRole() checks)
                    for (String role : roles) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }

                    // Add permissions as authorities (for hasAuthority() checks)
                    for (String permission : permissions) {
                        authorities.add(new SimpleGrantedAuthority(permission));
                    }

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in context
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.info("JWT Filter: Authenticated {} for {} {} | authorities={}",
                            username, method, uri, authorities);
                } else {
                    try {
                        Jwt jwt = keycloakJwtDecoder.decode(token);
                        username = jwt.getClaimAsString("preferred_username");
                        if (username == null || username.isBlank()) {
                            username = jwt.getSubject();
                        }
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
                        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                            for (Object r : roles) {
                                if (r != null) {
                                    authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
                                }
                            }
                        }
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(username, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.info("JWT Filter: Keycloak token for {} {} | authorities={}", username, uri, authorities);
                    } catch (JwtException e) {
                        log.warn("JWT Filter: Token not valid for {} {}: {}", method, uri, e.getMessage());
                    }
                }

                if (!shouldSkipAbac(request) && SecurityContextHolder.getContext().getAuthentication() != null) {
                    if (userId == null && username != null) {
                        userId = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
                    }
                    String clientIp = ClientIpResolver.resolve(request);
                    boolean allowed = lightAbacService.isAccessAllowed(userId, username != null ? username : "unknown", clientIp);
                    if (!allowed) {
                        log.warn("JWT Filter: IP/ABAC denied user={} ip={} uri={}", username, clientIp, uri);
                        SecurityContextHolder.clearContext();
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied by IP/business-hours policy");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.error("JWT Filter: Error processing authentication for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldSkipAbac(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/v1/auth/login")
            || uri.startsWith("/api/v1/auth/refresh")
            || uri.startsWith("/api/v1/auth/validate")
            || uri.startsWith("/api/v1/auth/mfa/verify")
            || uri.startsWith("/.well-known")
            || uri.startsWith("/oauth2/jwks")
            || uri.startsWith("/actuator")
            || uri.startsWith("/h2-console");
    }

    /**
     * Extract JWT token from the Authorization header.
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
