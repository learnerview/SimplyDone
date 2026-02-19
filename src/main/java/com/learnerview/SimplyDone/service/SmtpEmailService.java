package com.learnerview.SimplyDone.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for sending emails using SMTP (Gmail).
 * Follows Rule 1 (Strict Layered Responsibility) and Rule 6 (No Silent Failures).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${simplydone.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Send an email using JavaMailSender
     * 
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body (HTML)
     * @return Result map
     * @throws MessagingException if email sending fails
     */
    public Map<String, Object> sendEmail(String to, String subject, String body) throws MessagingException {
        if (!emailEnabled) {
            log.warn("Email dispatch skipped to {}: Service is explicitly disabled in application.properties (simplydone.email.enabled=false). This is expected if the environment is not configured for mail delivery.", to);
            return Map.of(
                "success", false,
                "message", "Email service is disabled in configuration",
                "simulated", true
            );
        }

        log.info("Initiating SMTP dispatch for {} with subject [{}]. Using Gmail SMTP strategy as configured in application.properties.", to, subject);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // Set to true for HTML
            
            mailSender.send(message);
            
            log.info("Email sent successfully to: {}", to);
            
            return Map.of(
                "success", true,
                "to", to,
                "subject", subject
            );
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw e; // Rule 6: No Silent Failures
        }
    }
}
