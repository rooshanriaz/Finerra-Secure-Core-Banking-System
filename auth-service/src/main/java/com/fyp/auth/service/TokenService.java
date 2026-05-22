package com.fyp.auth.service;

import com.fyp.auth.config.JwtProperties;
import com.fyp.auth.entity.Permission;
import com.fyp.auth.entity.Role;
import com.fyp.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for JWT token generation and validation.
 */
@Slf4j
@Service
public class TokenService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private final OidcKeyService oidcKeyService;
    
    // Lazy injection to avoid circular dependency
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private RevocationService revocationService;

    public TokenService(JwtProperties jwtProperties, OidcKeyService oidcKeyService) {
        this.jwtProperties = jwtProperties;
        this.oidcKeyService = oidcKeyService;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate access token for a user.
     */
    public String generateAccessToken(User user) {
        return generateToken(user, jwtProperties.getAccessTokenExpiration());
    }

    /**
     * Generate refresh token for a user.
     */
    public String generateRefreshToken(User user) {
        return generateToken(user, jwtProperties.getRefreshTokenExpiration());
    }

    /**
     * Generate a short-lived MFA token (5 minutes) used during the two-step login flow.
     * This token only proves the user passed password verification and must be
     * exchanged for a full access token after TOTP verification.
     */
    public String generateMfaToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(5 * 60 * 1000); // 5 minutes

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("userId", user.getId())
                .claim("type", "mfa")
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validate an MFA token (checks signature, expiry, and type claim).
     */
    public boolean validateMfaToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (claims.getExpiration().before(new Date())) {
                return false;
            }
            return "mfa".equals(claims.get("type", String.class));
        } catch (Exception e) {
            log.warn("Invalid MFA token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate a JWT token.
     */
    private String generateToken(User user, long expirationMs) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);
        String jti = UUID.randomUUID().toString();

        // Extract role names
        Set<String> roles = user.getRoles().stream()
                .filter(Role::isEnabled)
                .map(Role::getName)
                .collect(Collectors.toSet());

        // Extract permission codes
        Set<String> permissions = user.getRoles().stream()
                .filter(Role::isEnabled)
                .flatMap(role -> role.getPermissions().stream())
                .filter(Permission::isEnabled)
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        return generateToken(user, expirationMs, roles, permissions, false);
    }

    /**
     * Generate a JWT token with optional DID verification status.
     */
    public String generateAccessToken(User user, boolean didVerified) {
        Set<String> roles = user.getRoles().stream()
                .filter(Role::isEnabled)
                .map(Role::getName)
                .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
                .filter(Role::isEnabled)
                .flatMap(role -> role.getPermissions().stream())
                .filter(Permission::isEnabled)
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        return generateToken(user, jwtProperties.getAccessTokenExpiration(), roles, permissions, didVerified);
    }

    public String generateIdToken(User user, String audience) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuer(jwtProperties.getIssuer())
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("preferred_username", user.getUsername())
                .header().add("kid", oidcKeyService.getKeyId()).and()
                .signWith(oidcKeyService.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    private String generateToken(User user, long expirationMs, Set<String> roles,
                                 Set<String> permissions, boolean didVerified) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .id(jti)
                .subject(user.getUsername())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("userId", user.getId())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("fineractUserId", user.getFineractUserId())
                .claim("didVerified", didVerified)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validate a token.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            
            // Check expiration first (fast, no external calls)
            if (claims.getExpiration().before(new Date())) {
                log.warn("Token is expired for user: {}", claims.getSubject());
                return false;
            }

            // Check if token is revoked (may involve Redis/DB)
            String jti = claims.getId();
            if (jti != null && revocationService.isTokenRevoked(jti)) {
                log.warn("Token is revoked: {} for user: {}", jti, claims.getSubject());
                return false;
            }

            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error validating JWT token: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extract username from token.
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extract user ID from token.
     */
    public Long extractUserId(String token) {
        Object userId = extractAllClaims(token).get("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    /**
     * Extract JTI (JWT ID) from token.
     */
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    /**
     * Extract roles from token.
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(String token) {
        Object roles = extractAllClaims(token).get("roles");
        if (roles instanceof Collection) {
            return new HashSet<>((Collection<String>) roles);
        }
        return Set.of();
    }

    /**
     * Extract permissions from token.
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(String token) {
        Object permissions = extractAllClaims(token).get("permissions");
        if (permissions instanceof Collection) {
            return new HashSet<>((Collection<String>) permissions);
        }
        return Set.of();
    }

    /**
     * Extract expiration date from token.
     */
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /**
     * Extract all claims from token.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get token expiration in milliseconds.
     */
    public long getAccessTokenExpiration() {
        return jwtProperties.getAccessTokenExpiration();
    }

    /**
     * Get refresh token expiration in milliseconds.
     */
    public long getRefreshTokenExpiration() {
        return jwtProperties.getRefreshTokenExpiration();
    }
}
