package com.fyp.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Gateway route configuration.
 * 
 * Defines all routes from the API Gateway to backend services.
 */
@Slf4j
@Configuration
public class GatewayConfig {

    @Value("${services.core-banking-connector.url:http://localhost:8081}")
    private String coreBankingConnectorUrl;

    @Value("${services.auth-service.url:http://localhost:8082}")
    private String authServiceUrl;

    @Value("${services.kyc-aml-service.url:http://localhost:8083}")
    private String kycAmlServiceUrl;

    @Value("${services.transaction-service.url:http://localhost:8085}")
    private String transactionServiceUrl;

    @Value("${services.audit-service.url:http://localhost:8086}")
    private String auditServiceUrl;

    @Value("${services.fraud-detection-service.url:http://localhost:8087}")
    private String fraudDetectionServiceUrl;

    /**
     * Configure all gateway routes.
     */
    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        log.info("Configuring gateway routes:");
        log.info("  Core Banking Connector: {}", coreBankingConnectorUrl);
        log.info("  Auth Service: {}", authServiceUrl);
        log.info("  KYC/AML Service: {}", kycAmlServiceUrl);
        log.info("  Transaction Service: {}", transactionServiceUrl);
        log.info("  Audit Service: {}", auditServiceUrl);
        log.info("  Fraud Detection Service: {}", fraudDetectionServiceUrl);

        return builder.routes()
                // ============================================================
                // Auth Service Routes (Phase 3 - Enhanced Authentication)
                // ============================================================
                .route("auth-service-login", r -> r
                        .path("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/validate", "/api/v1/auth/mfa/verify")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "auth-service")
                        )
                        .uri(authServiceUrl))

                .route("auth-service-mfa", r -> r
                        .path("/api/v1/auth/mfa/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "auth-service-mfa")
                        )
                        .uri(authServiceUrl))

                .route("auth-service-logout", r -> r
                        .path("/api/v1/auth/logout", "/api/v1/auth/me")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "auth-service")
                        )
                        .uri(authServiceUrl))

                .route("auth-service-users", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "auth-service-users")
                        )
                        .uri(authServiceUrl))

                .route("auth-service-roles", r -> r
                        .path("/api/v1/auth-roles", "/api/v1/auth-roles/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/auth-roles(?<suffix>/.*)?", "/api/v1/roles${suffix}")
                                .addResponseHeader("X-Gateway-Route", "auth-service-roles")
                        )
                        .uri(authServiceUrl))

                .route("auth-service-admin", r -> r
                        .path("/api/v1/admin/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "auth-service-admin")
                        )
                        .uri(authServiceUrl))

                // ============================================================
                // Fineract Authentication Routes (Legacy - via CBC)
                // ============================================================
                .route("fineract-auth-route", r -> r
                        .path("/api/v1/authentication/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/authentication/(?<segment>.*)", "/api/v1/authentication/${segment}")
                                .addResponseHeader("X-Gateway-Route", "fineract-auth")
                        )
                        .uri(coreBankingConnectorUrl))

                // ============================================================
                // Client Management Routes (JWT Required)
                // ============================================================
                .route("clients-route", r -> r
                        .path("/api/v1/clients/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "clients")
                        )
                        .uri(coreBankingConnectorUrl))

                // ============================================================
                // Savings Account Routes (JWT Required)
                // ============================================================
                .route("savings-route", r -> r
                        .path("/api/v1/savingsaccounts/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "savings")
                        )
                        .uri(coreBankingConnectorUrl))

                // ============================================================
                // Loan Routes (JWT Required)
                // ============================================================
                .route("loans-route", r -> r
                        .path("/api/v1/loans/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "loans")
                        )
                        .uri(coreBankingConnectorUrl))

                // ============================================================
                // Loan Reschedule Routes (JWT Required)
                // ============================================================
                .route("reschedule-route", r -> r
                        .path("/api/v1/rescheduleloans/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "reschedule")
                        )
                        .uri(coreBankingConnectorUrl))

                // ============================================================
                // Transaction Routes (JWT Required)
                // ============================================================
                .route("transactions-route", r -> r
                        .path("/api/v1/journalentries/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "transactions")
                        )
                        .uri(coreBankingConnectorUrl))

                // ============================================================
                // Roles & Permissions Routes (JWT Required - Admin)
                // ============================================================
                .route("roles-route", r -> r
                        .path("/api/v1/roles/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "roles")
                        )
                        .uri(coreBankingConnectorUrl))

                .route("permissions-route", r -> r
                        .path("/api/v1/permissions/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "permissions")
                        )
                        .uri(coreBankingConnectorUrl))

                // ============================================================
                // Audit Routes (JWT Required - Admin)
                // ============================================================
                .route("audits-route", r -> r
                        .path("/api/v1/audits/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "audits")
                        )
                        .uri(coreBankingConnectorUrl))

                .route("reports-route", r -> r
                        .path("/api/v1/reports/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "reports")
                        )
                        .uri(coreBankingConnectorUrl))

                // ============================================================
                // KYC Routes (Phase 4 - KYC/AML with DID)
                // ============================================================
                .route("kyc-onboard-route", r -> r
                        .path("/api/v1/kyc/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "kyc-service")
                        )
                        .uri(kycAmlServiceUrl))

                // ============================================================
                // DID Routes (Phase 4 - Decentralized Identity)
                // ============================================================
                .route("did-route", r -> r
                        .path("/api/v1/did/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "did-service")
                        )
                        .uri(kycAmlServiceUrl))

                // ============================================================
                // AML Routes (Phase 4 - Anti-Money Laundering)
                // ============================================================
                .route("aml-route", r -> r
                        .path("/api/v1/aml/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "aml-service")
                        )
                        .uri(kycAmlServiceUrl))

                // ============================================================
                // Transaction Routes (Phase 5 - Secure Transactions)
                // ============================================================
                .route("transaction-service-route", r -> r
                        .path("/api/v1/transactions/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "transaction-service")
                        )
                        .uri(transactionServiceUrl))

                // ============================================================
                // Audit Trail Routes (Phase 5 - Blockchain Audit)
                // ============================================================
                .route("audit-service-route", r -> r
                        .path("/api/v1/audit/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "audit-service")
                        )
                        .uri(auditServiceUrl))

                // ============================================================
                // Fraud Detection Routes (Phase 6 - AI Fraud Detection)
                // ============================================================
                .route("fraud-detection-route", r -> r
                        .path("/api/v1/fraud/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "fraud-detection-service")
                        )
                        .uri(fraudDetectionServiceUrl))

                // ============================================================
                // Health Check Route (Public)
                // ============================================================
                .route("health-route", r -> r
                        .path("/health/**", "/actuator/**")
                        .filters(f -> f
                                .addResponseHeader("X-Gateway-Route", "health")
                        )
                        .uri(coreBankingConnectorUrl))

                .build();
    }

}
