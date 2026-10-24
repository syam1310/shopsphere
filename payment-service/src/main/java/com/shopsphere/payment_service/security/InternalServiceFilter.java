package com.shopsphere.payment_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceFilter extends OncePerRequestFilter {

    private static final String SECRET_HEADER =
            "X-Internal-Service-Secret";

    private static final String PAYMENT_LOOKUP_PATH =
            "/api/payments/internal/by-idempotency-key";

    private final byte[] expectedSecret;

    public InternalServiceFilter(
            @Value("${internal-service.secret}") String secret) {
        this.expectedSecret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI()
                .substring(request.getContextPath().length());

        return !("GET".equals(request.getMethod())
                && PAYMENT_LOOKUP_PATH.equals(requestPath));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String suppliedSecret = request.getHeader(SECRET_HEADER);

        if (suppliedSecret == null
                || !MessageDigest.isEqual(
                expectedSecret,
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid internal service credentials");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
