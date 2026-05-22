package com.fyp.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Propagates authenticated JWT identity to downstream services.
 * Token validation is performed by Spring Security OAuth2 Resource Server.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                // Principal can be anonymous or another Authentication type; only proceed when it's a JWT auth token.
                .ofType(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .flatMap(jwt -> {
                    // exchange.getAttributes() is a ConcurrentHashMap which rejects null values.
                    String userId = stringClaim(jwt, "userId", safe(jwt.getSubject(), "unknown"));
                    String username = stringClaim(jwt, "preferred_username", safe(jwt.getSubject(), "unknown"));
                    List<String> roles = extractRoles(jwt);

                    exchange.getAttributes().put("userId", userId);
                    exchange.getAttributes().put("username", username);
                    exchange.getAttributes().put("roles", roles);

                    ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Name", username)
                            .header("X-User-Roles", String.join(",", roles))
                            .header("X-Authenticated", "true")
                            .build();

                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                })
                // If not authenticated (no JwtAuthenticationToken principal), continue without adding headers.
                .switchIfEmpty(chain.filter(exchange));
    }

    private String stringClaim(Jwt jwt, String claim, String fallback) {
        Object value = jwt.getClaims().get(claim);
        return value != null ? String.valueOf(value) : safe(fallback, "unknown");
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        List<String> roles = new ArrayList<>();
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof java.util.Map<?, ?> map) {
            Object roleObj = map.get("roles");
            if (roleObj instanceof Collection<?> col) {
                for (Object r : col) roles.add(String.valueOf(r));
            }
        }
        Object directRoles = jwt.getClaims().get("roles");
        if (directRoles instanceof Collection<?> col) {
            for (Object r : col) {
                String role = String.valueOf(r);
                if (!roles.contains(role)) roles.add(role);
            }
        }
        return roles;
    }

    @Override
    public int getOrder() {
        return 10;
    }

}
