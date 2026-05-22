package com.fyp.gateway.security;

import com.fyp.gateway.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtUtil.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("testSecretKey1234567890123456789012345678901234567890");
        jwtProperties.setExpiration(86400000); // 24 hours
        jwtProperties.setHeader("Authorization");
        jwtProperties.setPrefix("Bearer ");
        jwtProperties.setPublicPaths(List.of("/api/v1/authentication/**"));

        jwtUtil = new JwtUtil(jwtProperties);
    }

    @Test
    void generateToken_ShouldCreateValidToken() {
        // Given
        String username = "testuser";
        String userId = "123";
        List<String> roles = List.of("USER", "ADMIN");

        // When
        String token = jwtUtil.generateToken(username, userId, roles);

        // Then
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void validateToken_ShouldReturnTrueForValidToken() {
        // Given
        String token = jwtUtil.generateToken("testuser", "123", List.of("USER"));

        // When
        boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void validateToken_ShouldReturnFalseForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean isValid = jwtUtil.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void extractUsername_ShouldReturnCorrectUsername() {
        // Given
        String username = "testuser";
        String token = jwtUtil.generateToken(username, "123", List.of("USER"));

        // When
        String extractedUsername = jwtUtil.extractUsername(token);

        // Then
        assertEquals(username, extractedUsername);
    }

    @Test
    void extractUserId_ShouldReturnCorrectUserId() {
        // Given
        String userId = "456";
        String token = jwtUtil.generateToken("testuser", userId, List.of("USER"));

        // When
        String extractedUserId = jwtUtil.extractUserId(token);

        // Then
        assertEquals(userId, extractedUserId);
    }

    @Test
    void extractRoles_ShouldReturnCorrectRoles() {
        // Given
        List<String> roles = List.of("USER", "ADMIN");
        String token = jwtUtil.generateToken("testuser", "123", roles);

        // When
        List<String> extractedRoles = jwtUtil.extractRoles(token);

        // Then
        assertEquals(roles.size(), extractedRoles.size());
        assertTrue(extractedRoles.containsAll(roles));
    }

    @Test
    void isTokenExpired_ShouldReturnFalseForValidToken() {
        // Given
        String token = jwtUtil.generateToken("testuser", "123", List.of("USER"));

        // When
        boolean isExpired = jwtUtil.isTokenExpired(token);

        // Then
        assertFalse(isExpired);
    }

    @Test
    void getPublicPaths_ShouldReturnConfiguredPaths() {
        // When
        List<String> publicPaths = jwtUtil.getPublicPaths();

        // Then
        assertNotNull(publicPaths);
        assertTrue(publicPaths.contains("/api/v1/authentication/**"));
    }

}
