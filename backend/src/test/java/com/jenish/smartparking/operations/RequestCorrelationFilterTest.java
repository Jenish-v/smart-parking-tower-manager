package com.jenish.smartparking.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void echoesAValidCallerRequestIdentifier() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "gate-17.request-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertEquals("gate-17.request-42", MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)));

        assertEquals(
                "gate-17.request-42",
                response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));
    }

    @Test
    void replacesAnInvalidIdentifierAndRestoresTheLoggingContext() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "invalid identifier");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "outer-request");

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            String generated = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
            UUID.fromString(generated);
        });

        UUID.fromString(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertEquals("outer-request", MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/facilities");
        request.setRequestURI("/api/v1/facilities");
        return request;
    }
}
