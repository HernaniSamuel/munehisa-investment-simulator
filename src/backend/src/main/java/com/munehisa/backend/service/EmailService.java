package com.munehisa.backend.service;

import com.munehisa.backend.exceptions.EmailSendException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine; // Thymeleaf

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    public void sendVerificationEmail(String email, String verificationToken) {
        Context context = new Context();
        context.setVariable("subject", "Email Verification");
        context.setVariable("message", "Click the button below to verify your email address:");
        context.setVariable("actionUrl", baseUrl + "/auth/verify?verificationToken=" + verificationToken);

        String content = templateEngine.process("email/auth-email", context);
        sendEmail(email, "Email Verification", content);
    }

    @Async
    public void sendPasswordRecoverEmail(String email, String resetToken) {
        Context context = new Context();
        context.setVariable("subject", "Password Reset Request");
        context.setVariable("message", "Click the button below to reset your password:");
        context.setVariable("actionUrl", baseUrl + "/auth/reset-password?token=" + resetToken);

        String content = templateEngine.process("email/auth-email", context);
        sendEmail(email, "Password Reset Request", content);
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
            throw new EmailSendException();
        }
    }
}