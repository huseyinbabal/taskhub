package io.github.huseyinbabal.taskhub.config;

import java.io.IOException;

import io.github.huseyinbabal.taskhub.notification.grpc.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id — reusing the caller's {@code X-Correlation-Id}
 * when supplied — and puts it on the MDC so it appears on log lines and is propagated
 * to notification-service by the gRPC client interceptor (SPEC §Session 3).
 *
 * <p>Runs first in the chain so even rejected requests are traceable, and echoes the id
 * back so a client can quote it when reporting a problem.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationId.generate();
        }

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        }
        finally {
            // Request threads are pooled — the id must not bleed into the next request.
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
