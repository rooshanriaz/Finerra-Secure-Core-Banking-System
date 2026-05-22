package com.fyp.gateway;

import com.fyp.gateway.config.OAuth2GatewayProperties;
import com.fyp.gateway.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * API Gateway Application
 * 
 * Single entry point for all client requests to the banking platform.
 * Provides:
 * - OAuth2/JWT Authentication
 * - Rate Limiting
 * - Request Routing
 * - Request/Response Logging
 */
@SpringBootApplication
@EnableConfigurationProperties({OAuth2GatewayProperties.class, RateLimitProperties.class})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
