package com.fyp.cbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Configuration properties for Apache Fineract connection.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "fineract")
public class FineractProperties {
    
    /**
     * Base URL of the Fineract API (e.g., https://localhost:8443/fineract-provider/api)
     */
    @NotBlank(message = "Fineract base URL is required")
    private String baseUrl;
    
    /**
     * Default username for Fineract authentication
     */
    @NotBlank(message = "Fineract username is required")
    private String username;
    
    /**
     * Default password for Fineract authentication
     */
    @NotBlank(message = "Fineract password is required")
    private String password;
    
    /**
     * Tenant identifier for multi-tenant Fineract deployment
     */
    @NotBlank(message = "Tenant ID is required")
    private String tenantId = "default";
    
    /**
     * Connection timeout in milliseconds
     */
    @Positive
    private int connectionTimeout = 5000;
    
    /**
     * Read timeout in milliseconds
     */
    @Positive
    private int readTimeout = 30000;
    
    /**
     * Whether to skip SSL certificate verification (for development only)
     */
    private boolean skipSslVerification = false;

    /**
     * Enable mutual TLS for outbound Fineract calls.
     */
    private boolean mtlsEnabled = false;

    /**
     * Client certificate keystore path (PKCS12/JKS).
     */
    private String clientKeyStorePath;

    /**
     * Client certificate keystore password.
     */
    private String clientKeyStorePassword;

    /**
     * Trusted CA cert path or truststore path.
     */
    private String trustStorePath;

    /**
     * Trusted CA/truststore password (optional for PEM cert).
     */
    private String trustStorePassword;
}
