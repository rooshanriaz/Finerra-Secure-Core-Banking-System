package com.fyp.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Validates Keycloak access tokens (RS256) for requests that do not use legacy HS256 auth-service JWTs.
 */
@Configuration
public class KeycloakJwtDecoderConfig {

    @Bean
    public JwtDecoder keycloakJwtDecoder(
            @Value("${oauth2.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${oauth2.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
