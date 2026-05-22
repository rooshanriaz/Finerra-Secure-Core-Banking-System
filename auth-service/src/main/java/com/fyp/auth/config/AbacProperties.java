package com.fyp.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Light ABAC (Attribute-Based Access Control).
 * Supports IP whitelist and business hours restrictions.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "abac")
public class AbacProperties {

    /**
     * Enable/disable ABAC checks globally.
     */
    private boolean enabled = true;

    /**
     * IP Whitelist configuration.
     */
    private IpWhitelistConfig ipWhitelist = new IpWhitelistConfig();

    /**
     * Business hours configuration.
     */
    private BusinessHoursConfig businessHours = new BusinessHoursConfig();

    @Data
    public static class IpWhitelistConfig {
        /**
         * Enable/disable IP whitelist checking.
         */
        private boolean enabled = true;

        /**
         * List of allowed IP addresses or CIDR ranges.
         */
        private List<String> allowedIps = new ArrayList<>();
    }

    @Data
    public static class BusinessHoursConfig {
        /**
         * Enable/disable business hours checking.
         */
        private boolean enabled = false;

        /**
         * Timezone for business hours (e.g., "Asia/Karachi").
         */
        private String timezone = "Asia/Karachi";

        /**
         * Start hour of business day (0-23).
         */
        private int startHour = 9;

        /**
         * End hour of business day (0-23).
         */
        private int endHour = 18;

        /**
         * Days when access is allowed (1=Monday, 7=Sunday).
         */
        private List<Integer> allowedDays = List.of(1, 2, 3, 4, 5);
    }
}
