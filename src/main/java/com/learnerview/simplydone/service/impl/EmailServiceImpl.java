package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.scheduling.annotation.Async;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${simplydone.registration.sender-email}")
    private String senderEmail;

    @Value("${simplydone.registration.sender-name}")
    private String senderName;

    @Value("${simplydone.registration.app-url}")
    private String appUrl;

    @Override
    @Async
    public void sendOtpEmail(String email, String otp, String organizationName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, senderName);
            helper.setTo(email);
            helper.setSubject("SimplyDone - Email Verification Code");

            String htmlContent = String.format("""
                    <html>
                    <body style="font-family: Arial, sans-serif; background-color: #f5f5f5; padding: 20px;">
                        <div style="max-width: 600px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                            <h2 style="color: #333; margin-bottom: 20px;">Welcome to SimplyDone!</h2>
                            <p style="color: #666; font-size: 16px; margin-bottom: 20px;">
                                Organization: <strong>%s</strong>
                            </p>
                            <p style="color: #666; font-size: 16px; margin-bottom: 20px;">
                                Your verification code is:
                            </p>
                            <div style="background-color: #f0f0f0; padding: 20px; border-radius: 6px; text-align: center; margin-bottom: 30px;">
                                <p style="font-size: 32px; font-weight: bold; color: #2563eb; letter-spacing: 4px; margin: 0;">
                                    %s
                                </p>
                            </div>
                            <p style="color: #999; font-size: 14px; margin-bottom: 20px;">
                                This code expires in 10 minutes. Do not share it with anyone.
                            </p>
                            <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                            <p style="color: #999; font-size: 12px; margin-bottom: 5px;">
                                If you didn't request this email, you can safely ignore it.
                            </p>
                        </div>
                    </body>
                    </html>
                    """, organizationName != null ? organizationName : "New User", otp);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("OTP email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void sendWelcomeEmail(String email, String organizationName, String apiKey, String producerId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, senderName);
            helper.setTo(email);
            helper.setSubject("SimplyDone - Your API Credentials");

            String htmlContent = String.format("""
                    <html>
                    <body style="font-family: Arial, sans-serif; background-color: #f5f5f5; padding: 20px;">
                        <div style="max-width: 600px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                            <h2 style="color: #333; margin-bottom: 10px;">🎉 Welcome to SimplyDone!</h2>
                            <p style="color: #666; font-size: 16px; margin-bottom: 30px;">
                                Your account has been created successfully. Here are your API credentials:
                            </p>
                            
                            <div style="background-color: #f9f9f9; padding: 20px; border-left: 4px solid #2563eb; margin-bottom: 20px;">
                                <p style="color: #999; font-size: 12px; margin: 0 0 8px 0; text-transform: uppercase;">Organization Name</p>
                                <p style="font-size: 16px; color: #333; margin: 0; word-break: break-all;">%s</p>
                            </div>
                            
                            <div style="background-color: #f9f9f9; padding: 20px; border-left: 4px solid #2563eb; margin-bottom: 20px;">
                                <p style="color: #999; font-size: 12px; margin: 0 0 8px 0; text-transform: uppercase;">Producer ID</p>
                                <p style="font-size: 16px; color: #333; margin: 0; font-family: monospace; word-break: break-all;">%s</p>
                            </div>
                            
                            <div style="background-color: #f9f9f9; padding: 20px; border-left: 4px solid #2563eb; margin-bottom: 30px;">
                                <p style="color: #999; font-size: 12px; margin: 0 0 8px 0; text-transform: uppercase;">API Key (keep this secret!)</p>
                                <p style="font-size: 14px; color: #d32f2f; margin: 0; font-family: monospace; word-break: break-all; font-weight: bold;">%s</p>
                            </div>
                            
                            <h3 style="color: #333; margin-top: 30px; margin-bottom: 15px;">Quick Start:</h3>
                            <p style="color: #666; font-size: 14px; margin-bottom: 10px;">Submit a job using curl:</p>
                            <div style="background-color: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 6px; overflow-x: auto; margin-bottom: 20px; font-family: monospace; font-size: 12px;">
curl -X POST %s/api/jobs \\<br/>
&nbsp;&nbsp;-H "X-API-KEY: %s" \\<br/>
&nbsp;&nbsp;-H "Content-Type: application/json" \\<br/>
&nbsp;&nbsp;-d '{<br/>
&nbsp;&nbsp;&nbsp;&nbsp;"jobType": "webhook",<br/>
&nbsp;&nbsp;&nbsp;&nbsp;"payload": {"url": "https://example.com/webhook"},<br/>
&nbsp;&nbsp;&nbsp;&nbsp;"nextRunAt": "2026-05-10T10:00:00Z"<br/>
&nbsp;&nbsp;}'
                            </div>
                            
                            <h3 style="color: #333; margin-top: 30px; margin-bottom: 15px;">Next Steps:</h3>
                            <ul style="color: #666; font-size: 14px; margin: 10px 0; padding-left: 20px;">
                                <li style="margin-bottom: 8px;">Visit the <a href="%s" style="color: #2563eb; text-decoration: none;">SimplyDone Dashboard</a> to manage your jobs</li>
                                <li style="margin-bottom: 8px;">Check the API documentation for more endpoints</li>
                                <li>Keep your API key secure and never commit it to version control</li>
                            </ul>
                            
                            <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                            <p style="color: #999; font-size: 12px; margin: 0;">
                                If you have any questions, reach out to our support team. Happy scheduling! 🚀
                            </p>
                        </div>
                    </body>
                    </html>
                    """, organizationName, producerId, apiKey, appUrl, apiKey, appUrl);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Welcome email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", email, e.getMessage(), e);
        }
    }
}
