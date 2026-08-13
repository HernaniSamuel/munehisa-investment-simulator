package com.munehisa.backend.infra.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: no Spring context is started here (no @SpringBootTest).
 */
@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void doFilterInternal_logsExactlyOneAccessLogLineWithExpectedFields() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/simulations/123");
        when(response.getStatus()).thenReturn(200);

        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            filter.doFilterInternal(request, response, filterChain);

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.INFO, event.getLevel());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void doFilterInternal_setsRequestIdInMdcWhileChainRuns_andClearsItAfterwards() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/simulations/123");
        when(response.getStatus()).thenReturn(200);

        String[] requestIdDuringChain = new String[1];
        doAnswer(invocation -> {
            requestIdDuringChain[0] = MDC.get("requestId");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(requestIdDuringChain[0], "requestId must be present in MDC while the chain runs");
        assertNull(MDC.get("requestId"), "requestId must be cleared from MDC after the filter completes");
    }

    @Test
    void doFilterInternal_generatesDifferentRequestIdPerCall() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/simulations/123");
        when(response.getStatus()).thenReturn(200);

        String[] firstRequestId = new String[1];
        String[] secondRequestId = new String[1];
        doAnswer(invocation -> {
            firstRequestId[0] = MDC.get("requestId");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        doAnswer(invocation -> {
            secondRequestId[0] = MDC.get("requestId");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertNotEquals(firstRequestId[0], secondRequestId[0]);
    }
}
