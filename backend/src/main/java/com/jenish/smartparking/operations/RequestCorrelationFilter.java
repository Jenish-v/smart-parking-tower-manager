package com.jenish.smartparking.operations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class RequestCorrelationFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-ID";

    static final String REQUEST_ID_MDC_KEY = "request.id";

    private static final Pattern VALID_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        long startedAt = System.nanoTime();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            LOGGER.atInfo()
                    .addKeyValue("http.request.method", request.getMethod())
                    .addKeyValue("url.path", request.getRequestURI())
                    .addKeyValue("http.response.status_code", response.getStatus())
                    .addKeyValue("event.duration_ms", durationMillis)
                    .log("HTTP request completed");
            restoreRequestId(previousRequestId);
        }
    }

    private static String requestId(String supplied) {
        if (supplied != null && VALID_REQUEST_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private static void restoreRequestId(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(REQUEST_ID_MDC_KEY);
        } else {
            MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
        }
    }
}
