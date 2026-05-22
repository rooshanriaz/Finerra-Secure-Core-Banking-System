package com.fyp.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Core Banking Connector integration.
 * Used for syncing roles/permissions from Fineract.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "cbc")
public class CbcProperties {

    /**
     * Core Banking Connector base URL.
     */
    private String url = "http://localhost:8081";

    /**
     * Username for authenticating with CBC (Basic Auth).
     */
    private String username = "system";

    /**
     * Password for authenticating with CBC (Basic Auth).
     */
    private String password = "system123";

    /**
     * Sync configuration.
     */
    private SyncConfig sync = new SyncConfig();

    @Data
    public static class SyncConfig {
        /**
         * Enable/disable automatic sync with Fineract.
         */
        private boolean enabled = true;

        /**
         * Sync interval in milliseconds.
         * Default: 5 minutes (300000 ms)
         */
        private long interval = 300000;
    }
}
