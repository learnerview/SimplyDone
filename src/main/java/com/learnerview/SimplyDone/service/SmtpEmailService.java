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
    private String defaultFromAddress;

    @Value("${simplydone.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Send an email using JavaMailSender with optional custom credentials
     * 
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body (HTML)
     * @param customSender Optional custom sender email
     * @param customPassword Optional custom sender password
     * @return Result map
     * @throws MessagingException if email sending fails
     */
    public Map<String, Object> sendEmail(String to, String subject, String body, 
                                        String customSender, String customPassword) throws MessagingException {
        boolean defaultSenderConfigured = defaultFromAddress != null && !defaultFromAddress.trim().isEmpty();
        if (!emailEnabled && !defaultSenderConfigured && customSender == null) {
            log.warn("Email dispatch skipped to {}: Service is explicitly disabled and no SMTP credentials or custom sender provided.", to);
            return Map.of(
                "success", false,
                "message", "Email service is disabled in configuration",
                "simulated", true
            );
        }

        String from = customSender != null ? customSender : defaultFromAddress;
        log.info("Initiating SMTP dispatch for {} (from: {}) with subject [{}].", to, from, subject);
        
        try {
            JavaMailSender senderToUse = mailSender;
            
            // If custom credentials are provided, we need to configure a new sender or reconfigure the existing one
            // Note: JavaMailSender is usually a JavaMailSenderImpl in Spring Boot
            if (customSender != null && customPassword != null && mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl) {
                log.info("Using custom SMTP credentials for sender: {}", customSender);
                org.springframework.mail.javamail.JavaMailSenderImpl impl = (org.springframework.mail.javamail.JavaMailSenderImpl) mailSender;
                
                // We create a temporary sender to avoid polluting the singleton bean if possible, 
                // but for simplicity here we'll assume we can use the same instance parameters if careful,
                // or better, create a new instance for this specific call.
                org.springframework.mail.javamail.JavaMailSenderImpl customImpl = new org.springframework.mail.javamail.JavaMailSenderImpl();
                customImpl.setHost(impl.getHost());
                customImpl.setPort(impl.getPort());
                customImpl.setProtocol(impl.getProtocol());
                customImpl.setDefaultEncoding(impl.getDefaultEncoding());
                customImpl.setJavaMailProperties(impl.getJavaMailProperties());
                
                customImpl.setUsername(customSender);
                customImpl.setPassword(customPassword);
                senderToUse = customImpl;
            }

            MimeMessage message = senderToUse.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(appendSimplyDoneBranding(body), true); // Set to true for HTML
            
            senderToUse.send(message);
            
            log.info("Email sent successfully to: {} from: {}", to, from);
            
            return Map.of(
                "success", true,
                "to", to,
                "from", from,
                "subject", subject
            );
        } catch (Exception e) {
            log.error("Failed to send email to {} from {}: {}", to, from, e.getMessage());
            if (e instanceof MessagingException) throw (MessagingException) e;
            throw new MessagingException("Failed to send email", e);
        }
    }

    /** HTML body for the SMTP connectivity test email. */
    private static final String TEST_EMAIL_BODY =
            "<p>This is an automated SMTP connectivity test sent by <strong>SimplyDone</strong>.</p>"
            + "<p>If you received this message, your email configuration is working correctly.</p>";

    /**
     * Returns the configured default sender address (SMTP_USERNAME).
     */
    public String getDefaultFromAddress() {
        return defaultFromAddress;
    }

    /**
     * Sends a test email to verify SMTP connectivity.
     *
     * @param to Recipient address; if null or blank, defaults to the configured sender address.
     * @return Result map with at least {@code success}, {@code to}, {@code from}, {@code subject}.
     * @throws MessagingException if the SMTP handshake fails.
     */
    public Map<String, Object> sendTestEmail(String to) throws MessagingException {
        String recipient = (to != null && !to.trim().isEmpty()) ? to.trim() : defaultFromAddress;
        return sendEmail(recipient, "SimplyDone — SMTP connection test", TEST_EMAIL_BODY);
    }
    public Map<String, Object> sendEmail(String to, String subject, String body) throws MessagingException {
        return sendEmail(to, subject, body, null, null);
    }

    /**
     * Appends a SimplyDone branding footer to the email body.
     */
    private String appendSimplyDoneBranding(String body) {
        String footer = "<br/><hr style='border:none;border-top:1px solid #e5e7eb;margin-top:24px'/>"
            + "<p style='font-size:11px;color:#9ca3af;margin-top:8px;font-family:sans-serif'>"
            + "Sent by <strong>SimplyDone</strong> &mdash; open-source priority job scheduler."
            + "</p>";
        if (body != null && body.contains("</body>")) {
            return body.replaceFirst("(?i)</body>", footer + "</body>");
        }
        return (body != null ? body : "") + footer;
    }
}
