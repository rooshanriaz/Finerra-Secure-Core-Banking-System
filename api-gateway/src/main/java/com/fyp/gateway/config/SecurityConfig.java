package com.fyp.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, OAuth2GatewayProperties props) {
        String[] publicPaths = props.getPublicPaths().toArray(new String[0]);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(publicPaths).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }))
                .build();
    }

    /**
     * Build the JWT decoder from the JWK Set URI directly, avoiding the
     * issuer-mismatch that occurs when the gateway contacts Keycloak via the
     * Docker-internal hostname (keycloak:8080) but Keycloak reports its issuer
     * as http://localhost:8090/realms/finnera.
     *
     * issuer-uri  → used ONLY for token-level "iss" claim validation (public URL)
     * jwk-set-uri → used for fetching the signing keys (Docker-internal URL is fine)
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(OAuth2GatewayProperties props,
                                  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
                                  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        // Build decoder that fetches keys from the (Docker-internal) JWK Set endpoint
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        // Validate "iss" against the public issuer URL that Keycloak stamps in tokens.
        //
        // NOTE: We intentionally do NOT hard-require an "aud" claim here.
        // Many Keycloak client setups omit aud entirely (or set it to "account"),
        // which would cause every request to be rejected with 401 at the gateway.
        // Downstream services still validate signatures + issuer, and authorization is enforced by roles.
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator));
        return decoder;
    }
}
