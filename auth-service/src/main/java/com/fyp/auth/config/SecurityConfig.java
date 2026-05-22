package com.fyp.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the auth service.
 * Registers the JWT authentication filter to parse Bearer tokens
 * and establish Spring Security authentication context.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Password encoder bean.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Security filter chain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (no token needed)
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/validate",
                    "/api/v1/auth/mfa/verify",
                    "/.well-known/openid-configuration",
                    "/oauth2/jwks",
                    "/actuator/health",
                    "/actuator/info",
                    "/h2-console/**"
                ).permitAll()
                
                // Admin endpoints require ADMIN role
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            
            // Add JWT filter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Allow H2 console frames (dev only)
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }
}
