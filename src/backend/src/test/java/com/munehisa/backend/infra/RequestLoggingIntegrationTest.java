package com.munehisa.backend.infra;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.munehisa.backend.controllers.IntegrationTestBase;
import com.munehisa.backend.dto.LoginRequestDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check that a request handled by RequestLoggingFilter and a request that ends in
 * a mapped domain exception (handled by RestExceptionHandler) share the same requestId - the
 * mechanism issue #132 relies on to correlate an access-log line with the error line it caused.
 */
@Tag("integration")
class RequestLoggingIntegrationTest extends IntegrationTestBase {

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
    }

    @Test
    void wrongPasswordLogin_producesAccessLogAndWarnLog_sharingTheSameRequestId() throws Exception {
        createUser(user -> user.setVerified(true));
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "wrong-password");

        appender.start();
        rootLogger.addAppender(appender);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());

        List<ILoggingEvent> accessEvents = appender.list.stream()
                .filter(event -> "com.munehisa.backend.infra.logging.RequestLoggingFilter".equals(event.getLoggerName()))
                .toList();
        List<ILoggingEvent> warnEvents = appender.list.stream()
                .filter(event -> "com.munehisa.backend.infra.RestExceptionHandler".equals(event.getLoggerName()))
                .toList();

        assertEquals(1, accessEvents.size(), "exactly one access-log line per request");
        assertEquals(1, warnEvents.size(), "exactly one error-log line for the mapped exception");
        assertEquals(Level.WARN, warnEvents.get(0).getLevel());
        assertFalse(warnEvents.get(0).getFormattedMessage().contains("wrong-password"),
                "the submitted password must never reach the log line");

        String accessRequestId = accessEvents.get(0).getMDCPropertyMap().get("requestId");
        String warnRequestId = warnEvents.get(0).getMDCPropertyMap().get("requestId");
        assertNotNull(accessRequestId);
        assertEquals(accessRequestId, warnRequestId, "access-log and error-log lines must share the same requestId");
    }
}
