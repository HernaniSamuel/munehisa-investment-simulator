package com.munehisa.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    @Autowired
    JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    public void sendVerificationEmail(String email, String verificationToken) {
        String subject = "Email Verification";
        String path = "/auth/verify?token=" + verificationToken;
        String message = "Click the button below to verify your email address:";
        sendEmail(email, subject, path, message);
    }

    @Async
    public void sendPasswordRecoverEmail(String email, String resetToken) {
        String subject = "Password Reset Request";
        String path = "/auth/reset-password?token=" + resetToken;
        String message = "Click the button below to reset your password:";
        sendEmail(email, subject, path, message);
    }

    private void sendEmail(String email, String subject, String path, String message) {
        try {
            String actionUrl = baseUrl + path;

            String content = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                </head>
                <body style="
                    margin:0;
                    padding:0;
                    background-color:#0f172a;
                    font-family:Arial, Helvetica, sans-serif;
                ">
                
                <table width="100%%" cellpadding="0" cellspacing="0" border="0">
                    <tr>
                        <td align="center" style="padding:40px 20px;">
                
                            <table width="600" cellpadding="0" cellspacing="0" border="0"
                                   style="
                                        background:#ffffff;
                                        border-radius:16px;
                                        overflow:hidden;
                                        box-shadow:0 8px 30px rgba(0,0,0,.15);
                                   ">
                
                                <tr>
                                    <td style="
                                        background:linear-gradient(135deg,#2563eb,#7c3aed);
                                        padding:40px;
                                        text-align:center;
                                    ">
                                        <h1 style="
                                            margin:0;
                                            color:white;
                                            font-size:32px;
                                            font-weight:bold;
                                        ">
                                            Munehisa
                                        </h1>
                
                                        <p style="
                                            margin-top:10px;
                                            color:rgba(255,255,255,.85);
                                            font-size:16px;
                                        ">
                                            Secure Account Management
                                        </p>
                                    </td>
                                </tr>
                
                                <tr>
                                    <td style="padding:40px">
                
                                        <h2 style="
                                            margin-top:0;
                                            color:#111827;
                                            font-size:24px;
                                        ">
                                            %s
                                        </h2>
                
                                        <p style="
                                            color:#4b5563;
                                            line-height:1.7;
                                            font-size:16px;
                                        ">
                                            %s
                                        </p>
                
                                        <div style="text-align:center; margin:40px 0;">
                                            <a href="%s"
                                               style="
                                                   background:#2563eb;
                                                   color:white;
                                                   text-decoration:none;
                                                   padding:16px 32px;
                                                   border-radius:10px;
                                                   display:inline-block;
                                                   font-weight:bold;
                                                   font-size:16px;
                                               ">
                                                Continue
                                            </a>
                                        </div>
                
                                        <p style="
                                            color:#6b7280;
                                            font-size:14px;
                                            line-height:1.6;
                                        ">
                                            If the button doesn't work, copy and paste the URL below into your browser:
                                        </p>
                
                                        <p style="
                                            word-break:break-all;
                                            color:#2563eb;
                                            font-size:13px;
                                        ">
                                            %s
                                        </p>
                
                                    </td>
                                </tr>
                
                                <tr>
                                    <td style="
                                        background:#f8fafc;
                                        text-align:center;
                                        padding:24px;
                                        color:#64748b;
                                        font-size:13px;
                                    ">
                                        © 2026 Munehisa. All rights reserved.
                                    </td>
                                </tr>
                
                            </table>
                
                        </td>
                    </tr>
                </table>
                
                </body>
                </html>
                """.formatted(
                                    subject,
                                    subject,
                                    message,
                                    actionUrl,
                                    actionUrl
                            );

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(mimeMessage);
        } catch(Exception exception) {
            System.err.println("Failed to send email: " + exception.getMessage());
        }
    }
}
