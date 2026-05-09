package com.learnerview.simplydone.service;

/**
 * Service for OTP (One-Time Password) generation and validation.
 */
public interface OtpService {
    /**
     * Generate a random 6-digit OTP.
     */
    String generateOtp();
    
    /**
     * Validate OTP format.
     */
    boolean isValidOtpFormat(String otp);
}
