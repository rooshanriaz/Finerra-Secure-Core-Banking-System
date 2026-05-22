package com.fyp.kyc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Keycloak JWT validation without OIDC discovery to {@code localhost} from Docker (unreachable).
 * JWKS is loaded from the internal Keycloak URL; {@code iss} must match the browser-facing issuer.
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
