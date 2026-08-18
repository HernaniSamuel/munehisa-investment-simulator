package com.munehisa.backend.service;

import com.munehisa.backend.exceptions.EmailSendException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailService {
    private static final String VERIFICATION_SUBJECT_KEY = "email.verification.subject";
    private static final String VERIFICATION_MESSAGE_KEY = "email.verification.message";
    private static final String PASSWORD_RESET_SUBJECT_KEY = "email.passwordReset.subject";
    private static final String PASSWORD_RESET_MESSAGE_KEY = "email.passwordReset.message";

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine; // Thymeleaf
    private final MessageSource messageSource;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String email, String verificationToken, Locale locale) {
        Context context = new Context(locale);
        context.setVariable("subjectKey", VERIFICATION_SUBJECT_KEY);
        context.setVariable("messageKey", VERIFICATION_MESSAGE_KEY);
        context.setVariable("actionUrl", frontendUrl + "/verify-email?token=" + verificationToken);

        String subject = messageSource.getMessage(VERIFICATION_SUBJECT_KEY, null, locale);
        String content = templateEngine.process("email/auth-email", context);
        sendEmail(email, subject, content);
    }

    @Async
    public void sendPasswordRecoverEmail(String email, String resetToken, Locale locale) {
        Context context = new Context(locale);
        context.setVariable("subjectKey", PASSWORD_RESET_SUBJECT_KEY);
        context.setVariable("messageKey", PASSWORD_RESET_MESSAGE_KEY);
        context.setVariable("actionUrl", frontendUrl + "/reset-password?token=" + resetToken);

        String subject = messageSource.getMessage(PASSWORD_RESET_SUBJECT_KEY, null, locale);
        String content = templateEngine.process("email/auth-email", context);
        sendEmail(email, subject, content);
    }

    private void sendEmail(String email, String subject, String content) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(mimeMessage);
        } catch (Exception exception) {
            throw new EmailSendException(exception);
        }
    }
}