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

    /**
     * Request OTP for key recovery. Only works for emails that have an active API key.
     */
    void requestRecoveryOtp(String email);

    /**
     * Verify recovery OTP, revoke old key, issue a new one.
     */
    RegistrationResponse recoverKey(String email, String otp);
}
