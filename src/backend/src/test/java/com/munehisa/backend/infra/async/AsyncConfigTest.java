package com.munehisa.backend.infra.async;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.munehisa.backend.exceptions.EmailSendException;
import com.munehisa.backend.service.EmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.TestPropertySource;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Proves that a failure thrown by EmailService's @Async methods - which run on a
 * separate executor thread and therefore never reach RestExceptionHandler - is not
 * silently swallowed: it is caught by our AsyncUncaughtExceptionHandler and logged
 * with the original exception preserved as the cause.
 */
@SpringBootTest(classes = AsyncConfigTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.mail.username=noreply@example.com",
        "app.frontend-url=http://localhost:3000"
})
class AsyncConfigTest {

    @Configuration
    @EnableAsync
    @Import(AsyncConfig.class)
    static class TestConfig {

        @Bean
        JavaMailSender javaMailSender() throws Exception {
            JavaMailSender mailSender = mock(JavaMailSender.class);
            MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new MailSendException("SMTP connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));
            return mailSender;
        }

        @Bean
        SpringTemplateEngine templateEngine() {
            SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);
            when(templateEngine.process(anyString(), any())).thenReturn("<html></html>");
            return templateEngine;
        }

        @Bean
        EmailService emailService(JavaMailSender javaMailSender, SpringTemplateEngine templateEngine) {
            return new EmailService(javaMailSender, templateEngine);
        }
    }

    @Autowired
    private EmailService emailService;

    @Test
    void asyncEmailFailureIsLoggedWithOriginalExceptionAsCause() throws InterruptedException {
        Logger logger = (Logger) LoggerFactory.getLogger(CustomAsyncExceptionHandler.class);
        CountDownLatch logged = new CountDownLatch(1);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent eventObject) {
                super.append(eventObject);
                logged.countDown();
            }
        };
        appender.start();
        logger.addAppender(appender);

        try {
            emailService.sendVerificationEmail("user@example.com", "some-verification-token");

            assertTrue(logged.await(5, TimeUnit.SECONDS),
                    "expected the async failure to be logged within the timeout");

            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.ERROR, event.getLevel());

            assertNotNull(event.getThrowableProxy(), "logged event must carry the exception");
            assertEquals(EmailSendException.class.getName(), event.getThrowableProxy().getClassName());

            assertNotNull(event.getThrowableProxy().getCause(), "original exception must be preserved as the cause");
            assertEquals(MailSendException.class.getName(), event.getThrowableProxy().getCause().getClassName());
        } finally {
            logger.detachAppender(appender);
        }
    }
}
