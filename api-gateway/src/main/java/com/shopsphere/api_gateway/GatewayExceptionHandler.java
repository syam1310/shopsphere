package com.shopsphere.api_gateway;

import java.time.Instant;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/** Converts unavailable downstream services into a clear, retryable gateway response. */
@RestControllerAdvice
public class GatewayExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDownstreamUnavailable(
            ResourceAccessException exception,
            HttpServletRequest request) {
        log.warn("Downstream service unavailable for {}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "A ShopSphere service is temporarily unavailable. Please try again shortly.",
                "path", request.getRequestURI()
        ));
    }
}
