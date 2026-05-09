package com.learnerview.simplydone.service;

/**
 * Service for sending emails.
 */
public interface EmailService {
    /**
     * Send OTP verification email.
     */
    void sendOtpEmail(String email, String otp, String organizationName);
    
    /**
     * Send welcome email with API credentials.
     */
    void sendWelcomeEmail(String email, String organizationName, String apiKey, String producerId);
}
