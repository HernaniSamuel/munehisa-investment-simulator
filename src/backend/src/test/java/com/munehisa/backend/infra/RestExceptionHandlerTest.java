package com.munehisa.backend.infra;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.exceptions.AssetUnavailableException;
import com.munehisa.backend.exceptions.ExchangeRateUnavailableException;
import com.munehisa.backend.exceptions.InflationUnavailableException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The generic RuntimeException handler must never leak an exception's own message, class
 * name, or cause into the HTTP response - see issue #85. It must still log the full exception
 * server-side so the fault stays debuggable once the response body stops exposing it.
 */
class RestExceptionHandlerTest {

    private static final String GENERIC_MESSAGE = "An unexpected error occurred.";

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void runtimeExceptionHandler_returns500WithGenericSanitizedMessage() {
        RuntimeException exception = new RuntimeException("some secret internal detail, e.g. SQL constraint xyz");

        ResponseEntity<RestErrorMessage> response = handler.runtimeExceptionHandler(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        String message = response.getBody().getMessage();
        assertEquals(GENERIC_MESSAGE, message);
        assertFalse(message.contains("secret internal detail"));
        assertFalse(message.contains(RuntimeException.class.getName()));
        assertFalse(message.contains(RuntimeException.class.getSimpleName()));
    }

    @Test
    void runtimeExceptionHandler_sanitizesRegardlessOfExceptionContent() {
        ResponseEntity<RestErrorMessage> noMessageResponse = handler.runtimeExceptionHandler(new RuntimeException());
        assertEquals(GENERIC_MESSAGE, noMessageResponse.getBody().getMessage());

        ResponseEntity<RestErrorMessage> otherSubtypeResponse =
                handler.runtimeExceptionHandler(new IllegalStateException("internal invariant violated"));
        assertEquals(GENERIC_MESSAGE, otherSubtypeResponse.getBody().getMessage());
    }

    @Test
    void runtimeExceptionHandler_logsFullExceptionAtErrorLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            RuntimeException exception = new RuntimeException("secret detail");

            handler.runtimeExceptionHandler(exception);

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.ERROR, event.getLevel());

            assertNotNull(event.getThrowableProxy(), "logged event must carry the exception");
            assertEquals(RuntimeException.class.getName(), event.getThrowableProxy().getClassName());
            assertEquals("secret detail", event.getThrowableProxy().getMessage());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void assetUnavailableHandler_returns503WithOriginalMessageUnchanged() {
        AssetUnavailableException exception = new AssetUnavailableException("AAPL", null);

        ResponseEntity<RestErrorMessage> response = handler.assetUnavailableHandler(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(exception.getMessage(), response.getBody().getMessage());
    }

    @Test
    void exchangeRateUnavailableHandler_returns503WithOriginalMessageUnchanged() {
        ExchangeRateUnavailableException exception = new ExchangeRateUnavailableException("USD", "EUR", null);

        ResponseEntity<RestErrorMessage> response = handler.exchangeRateUnavailableHandler(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(exception.getMessage(), response.getBody().getMessage());
    }

    @Test
    void inflationUnavailableHandler_returns503WithOriginalMessageUnchanged() {
        InflationUnavailableException exception = new InflationUnavailableException(InflationCurrency.USD, null);

        ResponseEntity<RestErrorMessage> response = handler.inflationUnavailableHandler(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(exception.getMessage(), response.getBody().getMessage());
    }

    @Test
    void unavailableHandlers_doNotLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            handler.assetUnavailableHandler(new AssetUnavailableException("AAPL", null));
            handler.exchangeRateUnavailableHandler(new ExchangeRateUnavailableException("USD", "EUR", null));
            handler.inflationUnavailableHandler(new InflationUnavailableException(InflationCurrency.USD, null));

            assertTrue(appender.list.isEmpty(), "the 503 unavailable handlers must not log - only the generic handler does");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
