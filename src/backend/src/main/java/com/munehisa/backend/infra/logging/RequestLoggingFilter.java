package com.munehisa.backend.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Registered ahead of Spring Security's FilterChainProxy (which Spring Boot places at
 * {@code SecurityProperties.DEFAULT_FILTER_ORDER}, -100) by implementing {@link Ordered}
 * with {@link Ordered#HIGHEST_PRECEDENCE}. Without this, a request Security rejects before
 * reaching the controller - e.g. RestAuthenticationEntryPoint writing a 401 directly for a
 * missing/invalid JWT - never reaches this filter at all, so it would never get an
 * access-log line despite being exactly the kind of request this filter exists to record.
 */
@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {

    static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("request completed",
                    StructuredArguments.kv("method", request.getMethod()),
                    StructuredArguments.kv("path", request.getRequestURI()),
                    StructuredArguments.kv("status", response.getStatus()),
                    StructuredArguments.kv("durationMs", durationMs));
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}
