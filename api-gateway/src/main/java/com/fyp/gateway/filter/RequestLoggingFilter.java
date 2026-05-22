package com.fyp.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter for logging all incoming requests.
 * 
 * Logs:
 * - Request ID (generated)
 * - Method
 * - Path
 * - Query parameters
 * - Client IP
 * - User Agent
 * - User ID (if authenticated)
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String RESPONSE_TIME_HEADER = "X-Response-Time";
    private static final String REQUEST_TIME_ATTR = "requestTime";
    private static final String REQUEST_ID_ATTR = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Generate request ID
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        // Store in exchange attributes for later use
        exchange.getAttributes().put(REQUEST_TIME_ATTR, startTime);
        exchange.getAttributes().put(REQUEST_ID_ATTR, requestId);

        ServerHttpRequest request = exchange.getRequest();

        // Extract request info
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();
        String query = request.getURI().getQuery();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");

        // Log incoming request
        log.info("[{}] {} {} {} | IP: {} | UA: {}",
                requestId,
                method,
                path,
                query != null ? "?" + maskSensitiveParams(query) : "",
                clientIp,
                userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "unknown"
        );

        // Add request ID to the request header (for downstream services)
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        // Modify response to add headers BEFORE it's committed
        ServerHttpResponse response = exchange.getResponse();
        
        // Use beforeCommit to add headers before response is sent
        response.beforeCommit(() -> {
            long duration = System.currentTimeMillis() - startTime;
            
            // Add response headers
            response.getHeaders().add(REQUEST_ID_HEADER, requestId);
            response.getHeaders().add(RESPONSE_TIME_HEADER, duration + "ms");
            
            return Mono.empty();
        });

        // Continue filter chain and log response after completion
        return chain.filter(exchange.mutate().request(modifiedRequest).build())
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = response.getStatusCode() != null ? 
                            response.getStatusCode().value() : 0;
                    
                    // Get user info (set by JWT filter)
                    String userId = exchange.getAttribute("userId");
                    
                    if (statusCode >= 400) {
                        log.warn("[{}] {} {} | Status: {} | Duration: {}ms | User: {}",
                                requestId, method, path, statusCode, duration,
                                userId != null ? userId : "anonymous"
                        );
                    } else {
                        log.info("[{}] {} {} | Status: {} | Duration: {}ms | User: {}",
                                requestId, method, path, statusCode, duration,
                                userId != null ? userId : "anonymous"
                        );
                    }
                });
    }

    /**
     * Get client IP address, considering proxies.
     */
    private String getClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }

        return request.getRemoteAddress() != null ? 
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * Mask sensitive parameters in query string.
     */
    private String maskSensitiveParams(String query) {
        return query
                .replaceAll("(password=)[^&]*", "$1****")
                .replaceAll("(token=)[^&]*", "$1****")
                .replaceAll("(secret=)[^&]*", "$1****")
                .replaceAll("(apiKey=)[^&]*", "$1****");
    }

    @Override
    public int getOrder() {
        // Run first, before all other filters
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
