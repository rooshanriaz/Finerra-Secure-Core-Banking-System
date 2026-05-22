package com.fyp.audit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Keycloak JWT validation without relying on OIDC discovery (which may advertise localhost URLs
 * unreachable from other Docker containers). JWKS is fetched from the internal Keycloak URL;
 * {@code iss} in tokens must match {@code oauth2.jwt.issuer} (typically the browser-facing realm URL).
 */
@Configuration
public class KeycloakJwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${oauth2.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${oauth2.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
