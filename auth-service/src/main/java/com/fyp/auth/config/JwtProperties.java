package com.fyp.auth.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for JWT token generation and validation.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Secret key for signing JWT tokens.
     * Must be at least 256 bits (32 characters) for HS256.
     */
    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    /**
     * Access token expiration time in milliseconds.
     * Default: 1 hour (3600000 ms)
     */
    private long accessTokenExpiration = 3600000;

    /**
     * Refresh token expiration time in milliseconds.
     * Default: 7 days (604800000 ms)
     */
    private long refreshTokenExpiration = 604800000;

    /**
     * Token issuer identifier.
     */
    private String issuer = "auth-service";
}
