package com.fyp.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for rate limiting.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /**
     * Whether rate limiting is enabled.
     */
    private boolean enabled = true;

    /**
     * Rate limit for authenticated users (requests per minute).
     */
    private int authenticatedLimit = 100;

    /**
     * Rate limit for anonymous users (requests per minute).
     */
    private int anonymousLimit = 20;

    /**
     * Time window in seconds for rate limiting.
     */
    private int windowSeconds = 60;

    /**
     * Burst capacity multiplier.
     * Actual burst = limit * burstMultiplier
     */
    private double burstMultiplier = 1.5;

    /**
     * Whether to use Redis for distributed rate limiting.
     * If false, uses in-memory rate limiting.
     */
    private boolean useRedis = false;

}
