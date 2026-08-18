package com.munehisa.backend.service;

import com.munehisa.backend.exceptions.EmailSendException;
import com.munehisa.backend.infra.i18n.I18nConfig;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests: no Spring context is started here (no @SpringBootTest).
 * MockitoExtension only wires the {@code @Mock} fields below; {@code emailService}
 * is built manually so it can hold a real {@link MessageSource} (see setUp()).
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;

    private EmailService emailService;

    private static final String FRONTEND_URL = "https://munehisa.app";
    private static final String FROM_ADDRESS = "noreply@munehisa.com";
    private static final String RENDERED_CONTENT = "<html>rendered</html>";

    @BeforeEach
    void setUp() {
        // A real MessageSource (backed by the actual messages*.properties on the
        // classpath) is used instead of a mock so this test catches a missing or
        // mistyped message key, not just whatever a stub was told to return.
        MessageSource messageSource = new I18nConfig().messageSource();
        emailService = new EmailService(mailSender, templateEngine, messageSource);

        // EmailService's "from"/"frontendUrl" fields are populated by @Value in
        // production; they must be set via reflection here.
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

        emailService.sendVerificationEmail("ada@example.com", "verification-token", Locale.ENGLISH);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("email/auth-email"), contextCaptor.capture());
        Context context = contextCaptor.getValue();
        assertEquals(Locale.ENGLISH, context.getLocale());
        assertEquals("email.verification.subject", context.getVariable("subjectKey"));
        assertEquals("email.verification.message", context.getVariable("messageKey"));
        assertEquals(FRONTEND_URL + "/verify-email?token=verification-token", context.getVariable("actionUrl"));

        verify(mailSender).send(mimeMessage);
        assertEquals("Email Verification", mimeMessage.getSubject());
        assertEquals("ada@example.com", mimeMessage.getAllRecipients()[0].toString());
        assertEquals(FROM_ADDRESS, mimeMessage.getFrom()[0].toString());
        assertEquals(RENDERED_CONTENT, extractTextContent(mimeMessage));
    }

    @Test
    void sendVerificationEmail_ptBrLocale_resolvesPortugueseSubjectAndContext() throws Exception {
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/auth-email"), any(Context.class))).thenReturn(RENDERED_CONTENT);

        emailService.sendVerificationEmail("ada@example.com", "verification-token", ptBr);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("email/auth-email"), contextCaptor.capture());
        assertEquals(ptBr, contextCaptor.getValue().getLocale());

        verify(mailSender).send(mimeMessage);
        assertEquals("Verificação de E-mail", mimeMessage.getSubject());
    }

    @Test
    void sendPasswordRecoverEmail_buildsCorrectContextAndSendsRenderedContent() throws Exception {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/auth-email"), any(Context.class))).thenReturn(RENDERED_CONTENT);

        emailService.sendPasswordRecoverEmail("ada@example.com", "reset-token", Locale.ENGLISH);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("email/auth-email"), contextCaptor.capture());
        Context context = contextCaptor.getValue();
        assertEquals(Locale.ENGLISH, context.getLocale());
        assertEquals("email.passwordReset.subject", context.getVariable("subjectKey"));
        assertEquals("email.passwordReset.message", context.getVariable("messageKey"));
        assertEquals(FRONTEND_URL + "/reset-password?token=reset-token", context.getVariable("actionUrl"));

        verify(mailSender).send(mimeMessage);
        assertEquals("Password Reset Request", mimeMessage.getSubject());
        assertEquals("ada@example.com", mimeMessage.getAllRecipients()[0].toString());
        assertEquals(FROM_ADDRESS, mimeMessage.getFrom()[0].toString());
        assertEquals(RENDERED_CONTENT, extractTextContent(mimeMessage));
    }

    @Test
    void sendPasswordRecoverEmail_ptBrLocale_resolvesPortugueseSubjectAndContext() throws Exception {
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/auth-email"), any(Context.class))).thenReturn(RENDERED_CONTENT);

        emailService.sendPasswordRecoverEmail("ada@example.com", "reset-token", ptBr);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("email/auth-email"), contextCaptor.capture());
        assertEquals(ptBr, contextCaptor.getValue().getLocale());

        verify(mailSender).send(mimeMessage);
        assertEquals("Solicitação de Redefinição de Senha", mimeMessage.getSubject());
    }

    @Test
    void sendVerificationEmail_mailSendFailure_wrapsAsEmailSendException() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/auth-email"), any(Context.class))).thenReturn(RENDERED_CONTENT);
        MailSendException sendFailure = new MailSendException("SMTP server unavailable");
        doThrow(sendFailure).when(mailSender).send(mimeMessage);

        EmailSendException thrown = assertThrows(EmailSendException.class,
                () -> emailService.sendVerificationEmail("ada@example.com", "verification-token", Locale.ENGLISH));

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
                () -> emailService.sendPasswordRecoverEmail("ada@example.com", "reset-token", Locale.ENGLISH));

        assertSame(sendFailure, thrown.getCause());
    }
}
