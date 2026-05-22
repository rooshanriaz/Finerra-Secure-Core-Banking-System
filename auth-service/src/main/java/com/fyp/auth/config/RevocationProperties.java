package com.fyp.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for token revocation.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "revocation")
public class RevocationProperties {

    /**
     * Whether to use Redis for token revocation caching.
     * When false, only database is used.
     */
    private boolean useRedis = false;

    /**
     * Redis key prefix for revoked tokens.
     */
    private String keyPrefix = "revoked:token:";

    /**
     * Pub/Sub channel name for instant logout notifications.
     */
    private String channel = "token-revocation";

    /**
     * Cleanup interval for expired tokens in milliseconds.
     * Default: 1 hour (3600000 ms)
     */
    private long cleanupInterval = 3600000;
}
