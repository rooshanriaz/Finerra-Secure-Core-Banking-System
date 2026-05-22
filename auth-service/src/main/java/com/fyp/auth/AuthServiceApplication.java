package com.fyp.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auth Service Application
 * 
 * Provides enhanced authentication and authorization with:
 * - OAuth2 Authorization Server for JWT token issuance
 * - Dynamic RBAC synced with Apache Fineract
 * - Redis-based token revocation with Pub/Sub
 * - Light ABAC (IP whitelist + business hours)
 * - mTLS for secure inter-service communication
 * 
 * Port: 8082
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
