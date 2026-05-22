package com.fyp.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Global exception handler for the API Gateway.
 * 
 * Handles all exceptions and returns consistent JSON error responses.
 */
@Slf4j
@Order(-2) // Run before default Spring error handler
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else if (ex instanceof java.net.ConnectException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Backend service is unavailable. Please try again later.";
            log.error("Connection error: {}", ex.getMessage());
        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = "Request timed out. Please try again.";
            log.error("Timeout error: {}", ex.getMessage());
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred.";
            log.error("Unexpected error: ", ex);
        }

        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String path = exchange.getRequest().getPath().value();

        log.warn("[{}] Error handling request to {}: {} - {}", 
                requestId, path, status.value(), message);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"success\":false,\"message\":\"%s\",\"status\":%d,\"error\":\"%s\",\"path\":\"%s\",\"timestamp\":\"%s\",\"requestId\":\"%s\"}",
                escapeJson(message),
                status.value(),
                status.getReasonPhrase(),
                escapeJson(path),
                Instant.now().toString(),
                requestId != null ? requestId : "unknown"
        );

        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body.getBytes())
        ));
    }

    /**
     * Escape special characters for JSON.
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

}
