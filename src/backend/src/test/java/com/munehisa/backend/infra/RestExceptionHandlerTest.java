package com.munehisa.backend.infra;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.dto.AccountLockedResponseDTO;
import com.munehisa.backend.exceptions.AccountLockedException;
import com.munehisa.backend.exceptions.AssetNotFoundException;
import com.munehisa.backend.exceptions.AssetUnavailableException;
import com.munehisa.backend.exceptions.EmailSendException;
import com.munehisa.backend.exceptions.ExchangeRateUnavailableException;
import com.munehisa.backend.exceptions.InflationUnavailableException;
import com.munehisa.backend.exceptions.InsufficientCashBalanceException;
import com.munehisa.backend.exceptions.InvalidCredentialsException;
import com.munehisa.backend.exceptions.ResetPasswordTokenExpiredException;
import com.munehisa.backend.exceptions.ResetPasswordTokenNotFoundException;
import com.munehisa.backend.exceptions.TickerSearchUnavailableException;
import com.munehisa.backend.exceptions.VerificationTokenExpiredException;
import com.munehisa.backend.exceptions.VerificationTokenNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.math.BigDecimal;
import java.time.Instant;

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
    void tickerSearchUnavailableHandler_returns503WithOriginalMessageUnchanged() {
        TickerSearchUnavailableException exception = new TickerSearchUnavailableException(null);

        ResponseEntity<RestErrorMessage> response = handler.tickerSearchUnavailableHandler(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(exception.getMessage(), response.getBody().getMessage());
    }

    @Test
    void unavailableHandlers_logAtErrorLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            handler.assetUnavailableHandler(new AssetUnavailableException("AAPL", null));
            handler.exchangeRateUnavailableHandler(new ExchangeRateUnavailableException("USD", "EUR", null));
            handler.inflationUnavailableHandler(new InflationUnavailableException(InflationCurrency.USD, null));
            handler.tickerSearchUnavailableHandler(new TickerSearchUnavailableException(null));

            assertEquals(4, appender.list.size(), "each 503 unavailable handler must log once, at ERROR level");
            appender.list.forEach(event -> assertEquals(Level.ERROR, event.getLevel()));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void emailSendHandler_logsAtErrorLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            handler.emailSendHandler(new EmailSendException(new RuntimeException("smtp timeout")));

            assertEquals(1, appender.list.size());
            assertEquals(Level.ERROR, appender.list.get(0).getLevel());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void invalidCredentialsHandler_logsAtWarnLevelWithTypeAndMessage_andNeverLeaksThePassword() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            handler.invalidCredentialsHandler(new InvalidCredentialsException());

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.WARN, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("InvalidCredentialsException"));
            // InvalidCredentialsException takes no constructor argument - it can never carry the
            // submitted password in the first place, so logging its message verbatim is safe.
            assertEquals("Invalid Credentials", new InvalidCredentialsException().getMessage());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void assetNotFoundHandler_logsAtWarnLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            handler.assetNotFoundHandler(new AssetNotFoundException("ZZZZ", null));

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.WARN, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("AssetNotFoundException"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void insufficientCashBalanceHandler_logsAtWarnLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            handler.insufficientCashBalanceHandler(
                    new InsufficientCashBalanceException(BigDecimal.valueOf(100), BigDecimal.valueOf(50)));

            assertEquals(1, appender.list.size());
            assertEquals(Level.WARN, appender.list.get(0).getLevel());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void accountLockedHandler_logsAtWarnLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            ResponseEntity<AccountLockedResponseDTO> response =
                    handler.accountLockedHandler(new AccountLockedException(Instant.now()));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertEquals(1, appender.list.size());
            assertEquals(Level.WARN, appender.list.get(0).getLevel());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void tokenAndCredentialExceptions_carryOnlyStaticSecretFreeMessages() {
        // These handlers log exception.getMessage() verbatim (via logWarn). That's only safe
        // because none of these exceptions accept the raw token/password as a constructor
        // argument - each message is a fixed string with no dynamic/secret content.
        assertEquals("Verification token not found.", new VerificationTokenNotFoundException().getMessage());
        assertEquals("Verification token has expired.", new VerificationTokenExpiredException().getMessage());
        assertEquals("Reset password token not found.", new ResetPasswordTokenNotFoundException().getMessage());
        assertEquals("Reset password token has expired.", new ResetPasswordTokenExpiredException().getMessage());
        assertEquals("Invalid Credentials", new InvalidCredentialsException().getMessage());
    }

    @Test
    void handleMethodArgumentNotValid_logsAtWarnLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MethodParameter parameter = Mockito.mock(MethodParameter.class);
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
            bindingResult.addError(new FieldError("target", "email", "must not be blank"));
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

            handler.handleMethodArgumentNotValid(
                    exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, Mockito.mock(WebRequest.class));

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.WARN, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("email"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
