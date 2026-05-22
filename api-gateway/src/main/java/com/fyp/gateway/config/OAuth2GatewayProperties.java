package com.fyp.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "gateway.security")
public class OAuth2GatewayProperties {
    private String audience = "api-gateway";
    private List<String> publicPaths = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/validate",
            "/api/v1/auth/mfa/verify",
            "/actuator/**",
            "/health/**"
    );
}
