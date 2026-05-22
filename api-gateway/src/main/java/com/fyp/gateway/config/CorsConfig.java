package com.fyp.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration for the API Gateway.
 * 
 * Allows cross-origin requests from frontend applications.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Allow all origins in development (configure properly in production)
        corsConfig.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "*"
        ));
        
        // Allowed methods
        corsConfig.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // Allowed headers
        corsConfig.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "X-User-Id",
                "X-Request-Id"
        ));
        
        // Exposed headers (visible to client)
        corsConfig.setExposedHeaders(List.of(
                "X-Request-Id",
                "X-Response-Time",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Window"
        ));
        
        // Allow credentials (cookies, authorization headers)
        corsConfig.setAllowCredentials(true);
        
        // Cache preflight response for 1 hour
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

}
