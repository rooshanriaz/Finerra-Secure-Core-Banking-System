package com.fyp.gateway.filter;

import com.fyp.gateway.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global filter for rate limiting using Token Bucket algorithm.
 * 
 * Uses in-memory rate limiting with Bucket4j.
 * Can be extended to use Redis for distributed rate limiting.
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitProperties rateLimitProperties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String key = resolveKey(request);
        boolean isAuthenticated = isAuthenticatedRequest(request);

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(isAuthenticated));
        
        long availableBefore = bucket.getAvailableTokens();
        log.debug("Rate limit check - Key: {}, Available tokens: {}, Authenticated: {}", 
                key, availableBefore, isAuthenticated);

        if (bucket.tryConsume(1)) {
            // Get rate limit info for headers
            long remainingTokens = bucket.getAvailableTokens();
            int limit = isAuthenticated ? 
                    rateLimitProperties.getAuthenticatedLimit() : 
                    rateLimitProperties.getAnonymousLimit();
            int windowSeconds = rateLimitProperties.getWindowSeconds();

            // Store in exchange attributes for adding headers later
            exchange.getAttributes().put("rateLimitLimit", limit);
            exchange.getAttributes().put("rateLimitRemaining", remainingTokens);
            exchange.getAttributes().put("rateLimitWindow", windowSeconds);

            // Add headers using beforeCommit to ensure they're in the final response
            ServerHttpResponse response = exchange.getResponse();
            response.beforeCommit(() -> {
                response.getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
                response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(remainingTokens));
                response.getHeaders().add("X-RateLimit-Window", windowSeconds + "s");
                return Mono.empty();
            });

            return chain.filter(exchange);
        } else {
            log.warn("Rate limit exceeded for key: {}", key);
            return onRateLimitExceeded(exchange);
        }
    }

    /**
     * Resolve the rate limit key.
     * Uses User ID for authenticated requests, IP address for anonymous.
     */
    private String resolveKey(ServerHttpRequest request) {
        // Check for user ID header (set by JWT filter)
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }

        // Fall back to IP address
        String ip = request.getRemoteAddress() != null ? 
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        
        // Check for X-Forwarded-For header (if behind proxy)
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            ip = forwardedFor.split(",")[0].trim();
        }

        return "ip:" + ip;
    }

    /**
     * Check if request is authenticated.
     */
    private boolean isAuthenticatedRequest(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authHeader != null && authHeader.startsWith("Bearer ");
    }

    /**
     * Create a rate limit bucket.
     */
    private Bucket createBucket(boolean isAuthenticated) {
        int limit = isAuthenticated ? 
                rateLimitProperties.getAuthenticatedLimit() : 
                rateLimitProperties.getAnonymousLimit();
        
        int burstCapacity = (int) (limit * rateLimitProperties.getBurstMultiplier());
        Duration window = Duration.ofSeconds(rateLimitProperties.getWindowSeconds());

        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(burstCapacity)
                .refillGreedy(limit, window)
                .build();

        log.info("Created rate limit bucket - Limit: {}, Burst: {}, Window: {}s", 
                limit, burstCapacity, rateLimitProperties.getWindowSeconds());

        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Return 429 Too Many Requests response.
     */
    private Mono<Void> onRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getHeaders().add("Retry-After", String.valueOf(rateLimitProperties.getWindowSeconds()));
        response.getHeaders().add("X-RateLimit-Limit", 
                String.valueOf(rateLimitProperties.getAnonymousLimit()));
        response.getHeaders().add("X-RateLimit-Remaining", "0");
        response.getHeaders().add("X-RateLimit-Window", rateLimitProperties.getWindowSeconds() + "s");

        String body = String.format(
                "{\"success\":false,\"message\":\"Rate limit exceeded. Please try again in %d seconds.\",\"timestamp\":\"%s\"}",
                rateLimitProperties.getWindowSeconds(),
                java.time.Instant.now().toString()
        );

        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes())
        ));
    }

    @Override
    public int getOrder() {
        // Run early, before JWT authentication
        return -1;
    }

}
