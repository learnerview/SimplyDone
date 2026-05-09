package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.dto.RegistrationResponse;
import com.learnerview.simplydone.entity.ApiKeyEntity;
import com.learnerview.simplydone.entity.EmailVerificationEntity;
import com.learnerview.simplydone.repository.ApiKeyRepository;
import com.learnerview.simplydone.repository.EmailVerificationRepository;
import com.learnerview.simplydone.service.EmailService;
import com.learnerview.simplydone.service.OtpService;
import com.learnerview.simplydone.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RegistrationServiceImpl implements RegistrationService {

    private final EmailVerificationRepository emailVerificationRepo;
    private final ApiKeyRepository apiKeyRepo;
    private final EmailService emailService;
    private final OtpService otpService;

    @Value("${simplydone.registration.otp-validity-minutes:10}")
    private int otpValidityMinutes;

    @Value("${simplydone.registration.max-verification-attempts:3}")
    private int maxVerificationAttempts;

    @Override
    public void requestOtp(String email, String organizationName) {
        email = email.trim().toLowerCase();
        
        // Validate email format
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format");
        }

        // Check if email already verified (has active API key)
        var existingVerification = emailVerificationRepo.findByEmailAndVerifiedTrue(email);
        if (existingVerification.isPresent()) {
            throw new IllegalArgumentException("Email already registered. Please login with your API key.");
        }

        // Generate and save OTP
        String otp = otpService.generateOtp();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(otpValidityMinutes * 60L);

        var verification = EmailVerificationEntity.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .otpCode(otp)
                .verified(false)
                .verificationAttempts(0)
                .organizationName(organizationName)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        emailVerificationRepo.save(verification);

        // Send OTP email
        try {
            emailService.sendOtpEmail(email, otp, organizationName);
            log.info("OTP requested for email: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage());
            throw new RuntimeException("Failed to send OTP email. Please try again later.", e);
        }
    }

    @Override
    public RegistrationResponse verifyOtpAndCreateKey(String email, String otp) {
        email = email.trim().toLowerCase();

        // Validate OTP format
        if (!otpService.isValidOtpFormat(otp)) {
            throw new IllegalArgumentException("Invalid OTP format");
        }

        // Find pending verification
        var verification = emailVerificationRepo
                .findByEmailAndVerifiedFalseAndExpiresAtAfter(email, Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("No pending verification found. Please request a new OTP."));

        // Check OTP attempts
        if (verification.getVerificationAttempts() >= maxVerificationAttempts) {
            emailVerificationRepo.delete(verification);
            throw new IllegalArgumentException("Max verification attempts exceeded. Please request a new OTP.");
        }

        // Verify OTP
        if (!verification.getOtpCode().equals(otp)) {
            verification.setVerificationAttempts(verification.getVerificationAttempts() + 1);
            emailVerificationRepo.save(verification);
            throw new IllegalArgumentException("Invalid OTP. Please try again.");
        }

        // Mark as verified
        verification.setVerified(true);
        verification.setVerifiedAt(Instant.now());
        emailVerificationRepo.save(verification);

        // Create API key
        String producerId = generateProducerId(email);
        String apiKey = "sd_sk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        ApiKeyEntity keyEntity = ApiKeyEntity.builder()
                .id(UUID.randomUUID().toString())
                .apiKey(apiKey)
                .producer(producerId)
                .label(verification.getOrganizationName())
                .admin(false)
                .active(true)
                .createdAt(Instant.now())
                .build();

        apiKeyRepo.save(keyEntity);

        // Send welcome email
        try {
            emailService.sendWelcomeEmail(email, verification.getOrganizationName(), apiKey, producerId);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", email, e.getMessage());
            // Don't throw - key was already created, just log the error
        }

        log.info("New API key created for producer: {}", producerId);

        return RegistrationResponse.builder()
                .apiKey(apiKey)
                .producerId(producerId)
                .organizationName(verification.getOrganizationName())
                .message("Registration successful! Your API key has been sent to your email.")
                .build();
    }

    private String generateProducerId(String email) {
        // Generate deterministic producer ID from email hash
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(email.getBytes(StandardCharsets.UTF_8));
            String hashHex = bytesToHex(hash).substring(0, 12);
            return "prod_" + hashHex;
        } catch (NoSuchAlgorithmException e) {
            // Fallback to random if hash fails
            return "prod_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}
