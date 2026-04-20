package com.nightlypick.server.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = headerOrDefault(request.getHeader("X-Request-Id"), "req-" + UUID.randomUUID());
        String sessionId = headerOrDefault(request.getHeader("X-Session-Id"), "-");
        String traceId = headerOrDefault(request.getHeader("X-Trace-Id"), "trace-" + UUID.randomUUID());
        long startedAt = System.currentTimeMillis();

        MDC.put("requestId", requestId);
        MDC.put("sessionId", sessionId);
        MDC.put("traceId", traceId);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            log.info("收到请求 method={} uri={} query={} remoteAddr={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getQueryString(),
                    request.getRemoteAddr());
            filterChain.doFilter(request, response);
            log.info("请求处理完成 method={} uri={} status={} elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    System.currentTimeMillis() - startedAt);
        } finally {
            MDC.remove("requestId");
            MDC.remove("sessionId");
            MDC.remove("traceId");
        }
    }

    private String headerOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
