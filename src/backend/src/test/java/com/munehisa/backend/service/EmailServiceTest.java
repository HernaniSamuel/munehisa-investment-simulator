package com.munehisa.backend.service;

import com.munehisa.backend.exceptions.EmailSendException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests: no Spring context is started here (no @SpringBootTest).
 * MockitoExtension only wires the {@code @Mock}/{@code @InjectMocks} fields below.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    private static final String FRONTEND_URL = "https://munehisa.app";
    private static final String FROM_ADDRESS = "noreply@munehisa.com";
    private static final String RENDERED_CONTENT = "<html>rendered</html>";

    @BeforeEach
    void setUp() {
        // EmailService's "from"/"frontendUrl" fields are populated by @Value in
        // production. @InjectMocks only fills constructor-injected fields, so the
        // @Value fields must be set via reflection here.
        ReflectionTestUtils.setField(emailService, "from", FROM_ADDRESS);
        ReflectionTestUtils.setField(emailService, "frontendUrl", FRONTEND_URL);
    }

    // MimeMessageHelper requires a real MimeMessage to operate on, not a mock.
    private MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    // helper.setText(content, true) with multipart=true nests the HTML body inside
    // MimeMultipart wrappers, so the rendered content must be dug out recursively.
    private String extractTextContent(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String extracted = extractTextContent(multipart.getBodyPart(i));
                if (extracted != null) {
                    return extracted;
                }
            }
            return null;
        }
        return content instanceof String text ? text : null;
    }

    @Test
    void sendVerificationEmail_buildsCorrectContextAndSendsRenderedContent() throws Exception {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/auth-email"), any(Context.class))).thenReturn(RENDERED_CONTENT);

        emailService.sendVerificationEmail("ada@example.com", "verification-token");

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("email/auth-email"), contextCaptor.capture());
        Context context = contextCaptor.getValue();
        assertEquals("Email Verification", context.getVariable("subject"));
        assertEquals("Click the button below to verify your email address:", context.getVariable("message"));
        assertEquals(FRONTEND_URL + "/verify-email?token=verification-token", context.getVariable("actionUrl"));

        verify(mailSender).send(mimeMessage);
        assertEquals("Email Verification", mimeMessage.getSubject());
        assertEquals("ada@example.com", mimeMessage.getAllRecipients()[0].toString());
        assertEquals(FROM_ADDRESS, mimeMessage.getFrom()[0].toString());
        assertEquals(RENDERED_CONTENT, extractTextContent(mimeMessage));
    }

    @Test
    void sendPasswordRecoverEmail_buildsCorrectContextAndSendsRenderedContent() throws Exception {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/auth-email"), any(Context.class))).thenReturn(RENDERED_CONTENT);

        emailService.sendPasswordRecoverEmail("ada@example.com", "reset-token");

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("email/auth-email"), contextCaptor.capture());
        Context context = contextCaptor.getValue();
        assertEquals("Password Reset Request", context.getVariable("subject"));
        assertEquals("Click the button below to reset your password:", context.getVariable("message"));
        assertEquals(FRONTEND_URL + "/reset-password?token=reset-token", context.getVariable("actionUrl"));

        verify(mailSender).send(mimeMessage);
        assertEquals("Password Reset Request", mimeMessage.getSubject());
        assertEquals("ada@example.com", mimeMessage.getAllRecipients()[0].toString());
        assertEquals(FROM_ADDRESS, mimeMessage.getFrom()[0].toString());
        assertEquals(RENDERED_CONTENT, extractTextContent(mimeMessage));
    }

    @Test
    void sendVerificationEmail_mailSendFailure_wrapsAsEmailSendException() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/auth-email"), any(Context.class))).thenReturn(RENDERED_CONTENT);
        MailSendException sendFailure = new MailSendException("SMTP server unavailable");
        doThrow(sendFailure).when(mailSender).send(mimeMessage);

        EmailSendException thrown = assertThrows(EmailSendException.class,
                () -> emailService.sendVerificationEmail("ada@example.com", "verification-token"));

        assertSame(sendFailure, thrown.getCause());
    }

    @Test
    void sendPasswordRecoverEmail_mailSendFailure_wrapsAsEmailSendException() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/auth-email"), any(Context.class))).thenReturn(RENDERED_CONTENT);
        MailSendException sendFailure = new MailSendException("SMTP server unavailable");
        doThrow(sendFailure).when(mailSender).send(mimeMessage);

        EmailSendException thrown = assertThrows(EmailSendException.class,
                () -> emailService.sendPasswordRecoverEmail("ada@example.com", "reset-token"));

        assertSame(sendFailure, thrown.getCause());
    }
}
