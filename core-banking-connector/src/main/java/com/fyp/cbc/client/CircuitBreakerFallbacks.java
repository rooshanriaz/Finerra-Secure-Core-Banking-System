package com.fyp.cbc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fyp.cbc.exception.FineractApiException;
import com.fyp.cbc.exception.ResourceNotFoundException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * Resilience4j {@code @CircuitBreaker} fallbacks receive every failure from the decorated method.
 * Without this, Fineract {@link FineractApiException} (4xx/5xx with body) is replaced by a generic 503,
 * which hides validation errors and misleads operators.
 */
final class CircuitBreakerFallbacks {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerFallbacks.class);

    private CircuitBreakerFallbacks() {
    }

    /**
     * Re-throw Fineract / not-found errors unchanged; otherwise log and throw a generic 503.
     */
    static RuntimeException rethrowPreservingFineract(Throwable t, String operationLabel) {
        Throwable cur = t;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (cur instanceof FineractApiException fe) {
                return fe;
            }
            if (cur instanceof ResourceNotFoundException rn) {
                return rn;
            }
            if (cur instanceof CallNotPermittedException) {
                break;
            }
            cur = cur.getCause();
        }
        log.error("Circuit breaker fallback for {}: {}", operationLabel, t.getMessage());
        return new FineractApiException(
            "Service is temporarily unavailable. Please try again later.",
            503,
            "SERVICE_UNAVAILABLE");
    }
}
