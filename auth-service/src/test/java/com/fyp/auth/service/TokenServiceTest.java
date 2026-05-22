package com.fyp.auth.service;

import com.fyp.auth.config.JwtProperties;
import com.fyp.auth.entity.Permission;
import com.fyp.auth.entity.Role;
import com.fyp.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RevocationService revocationService;

    private TokenService tokenService;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("mySecretKey12345678901234567890123456789012");
        jwtProperties.setAccessTokenExpiration(3600000L);
        jwtProperties.setRefreshTokenExpiration(604800000L);
        jwtProperties.setIssuer("auth-service-test");

        tokenService = new TokenService(jwtProperties);
        // Inject mock RevocationService using reflection
        try {
            java.lang.reflect.Field field = TokenService.class.getDeclaredField("revocationService");
            field.setAccessible(true);
            field.set(tokenService, revocationService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldGenerateAccessToken() {
        // Given
        User user = createTestUser();

        // When
        String token = tokenService.generateAccessToken(user);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void shouldValidateToken() {
        // Given
        User user = createTestUser();
        String token = tokenService.generateAccessToken(user);
        when(revocationService.isTokenRevoked(anyString())).thenReturn(false);

        // When
        boolean valid = tokenService.validateToken(token);

        // Then
        assertTrue(valid);
    }

    @Test
    void shouldExtractUsername() {
        // Given
        User user = createTestUser();
        String token = tokenService.generateAccessToken(user);

        // When
        String username = tokenService.extractUsername(token);

        // Then
        assertEquals("testuser", username);
    }

    @Test
    void shouldExtractUserId() {
        // Given
        User user = createTestUser();
        String token = tokenService.generateAccessToken(user);

        // When
        Long userId = tokenService.extractUserId(token);

        // Then
        assertEquals(1L, userId);
    }

    @Test
    void shouldExtractRoles() {
        // Given
        User user = createTestUser();
        String token = tokenService.generateAccessToken(user);

        // When
        Set<String> roles = tokenService.extractRoles(token);

        // Then
        assertTrue(roles.contains("ADMIN"));
    }

    @Test
    void shouldRejectRevokedToken() {
        // Given
        User user = createTestUser();
        String token = tokenService.generateAccessToken(user);
        when(revocationService.isTokenRevoked(anyString())).thenReturn(true);

        // When
        boolean valid = tokenService.validateToken(token);

        // Then
        assertFalse(valid);
    }

    private User createTestUser() {
        Permission permission = Permission.builder()
                .id(1L)
                .code("READ_USER")
                .enabled(true)
                .build();

        Role role = Role.builder()
                .id(1L)
                .name("ADMIN")
                .enabled(true)
                .permissions(Set.of(permission))
                .build();

        return User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .roles(Set.of(role))
                .build();
    }
}
