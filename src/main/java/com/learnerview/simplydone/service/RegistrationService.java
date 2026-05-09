package com.learnerview.simplydone.service;

import com.learnerview.simplydone.dto.RegistrationResponse;

/**
 * Service for self-service API key registration.
 */
public interface RegistrationService {
    /**
     * Request OTP for email verification.
     */
    void requestOtp(String email, String organizationName);
    
    /**
     * Verify OTP and create API key.
     */
    RegistrationResponse verifyOtpAndCreateKey(String email, String otp);
}
