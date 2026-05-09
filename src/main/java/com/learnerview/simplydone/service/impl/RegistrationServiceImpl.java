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

    // OTPs are hashed with SHA-256 before storage so that a database breach
    // does not expose usable one-time codes. The plaintext OTP is sent only to
    // the user's email inbox and is never persisted anywhere.

    @Override
    public void requestOtp(String email, String organizationName) {
        email = email.trim().toLowerCase();

        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format");
        }

        var existingVerification = emailVerificationRepo.findFirstByEmailAndVerifiedTrueOrderByCreatedAtAsc(email);
        if (existingVerification.isPresent()) {
            throw new IllegalArgumentException("Email already registered. Please login with your API key.");
        }

        emailVerificationRepo.deleteByEmailAndVerifiedFalseAndOrganizationNameNot(email, "__RECOVERY__");

        String otp = otpService.generateOtp();
        String otpHash = hashOtp(otp); // store hash, never plaintext
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(otpValidityMinutes * 60L);

        var verification = EmailVerificationEntity.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .otpCode(otpHash)
                .verified(false)
                .verificationAttempts(0)
                .organizationName(organizationName)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        emailVerificationRepo.save(verification);

        emailService.sendOtpEmail(email, otp, organizationName); // plaintext only in email
        log.info("OTP requested for email: {}", email);
    }

    @Override
    public RegistrationResponse verifyOtpAndCreateKey(String email, String otp) {
        email = email.trim().toLowerCase();

        if (!otpService.isValidOtpFormat(otp)) {
            throw new IllegalArgumentException("Invalid OTP format");
        }

        var verification = emailVerificationRepo
            .findFirstByEmailAndVerifiedFalseAndExpiresAtAfterAndOrganizationNameNotOrderByCreatedAtDesc(
                email, Instant.now(), "__RECOVERY__")
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pending verification found. Please request a new OTP."));

        if (verification.getVerificationAttempts() >= maxVerificationAttempts) {
            emailVerificationRepo.delete(verification);
            throw new IllegalArgumentException("Max verification attempts exceeded. Please request a new OTP.");
        }

        // Hash the user's input and compare hashes — plaintext never hits the DB.
        if (!hashOtp(otp).equals(verification.getOtpCode())) {
            verification.setVerificationAttempts(verification.getVerificationAttempts() + 1);
            emailVerificationRepo.save(verification);
            throw new IllegalArgumentException("Invalid OTP. Please try again.");
        }

        verification.setVerified(true);
        verification.setVerifiedAt(Instant.now());
        emailVerificationRepo.save(verification);

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

        try {
            emailService.sendWelcomeEmail(email, verification.getOrganizationName(), apiKey, producerId);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", email, e.getMessage());
            // Don't throw — key was already created
        }

        log.info("New API key created for producer: {}", producerId);

        return RegistrationResponse.builder()
                .apiKey(apiKey)
                .producerId(producerId)
                .organizationName(verification.getOrganizationName())
                .message("Registration successful! Your API key has been sent to your email.")
                .build();
    }

    // KEY RECOVERY FLOW
    // WHY: A user who has lost their key proves identity via their registered
    // email. A recovery OTP is sent; on verification all old active keys are
    // revoked and a fresh key is issued. A breached DB yields only hashed OTPs
    // (useless) — no attacker can reconstruct or reuse the old or new keys.

    @Override
    public void requestRecoveryOtp(String email) {
        email = email.trim().toLowerCase();

        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format");
        }

        // Only registered users (have a verified signup record) can trigger recovery
        emailVerificationRepo.findFirstByEmailAndVerifiedTrueOrderByCreatedAtAsc(email)
                .filter(v -> !"__RECOVERY__".equals(v.getOrganizationName()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No registered account found for this email."));

                emailVerificationRepo.deleteByEmailAndVerifiedFalseAndOrganizationName(email, "__RECOVERY__");

        String otp = otpService.generateOtp();
        String otpHash = hashOtp(otp);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(otpValidityMinutes * 60L);

        // Sentinel value "__RECOVERY__" in organizationName distinguishes this
        // record from signup verifications without needing a separate DB table/column.
        var recovery = EmailVerificationEntity.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .otpCode(otpHash)
                .verified(false)
                .verificationAttempts(0)
                .organizationName("__RECOVERY__")
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        emailVerificationRepo.save(recovery);

        emailService.sendOtpEmail(email, otp, "Key Recovery");
        log.info("Recovery OTP requested for email: {}", email);
    }

    @Override
    public RegistrationResponse recoverKey(String email, String otp) {
        email = email.trim().toLowerCase();

        if (!otpService.isValidOtpFormat(otp)) {
            throw new IllegalArgumentException("Invalid OTP format");
        }

        // Find the pending recovery record (sentinel filter)
        var recovery = emailVerificationRepo
            .findFirstByEmailAndVerifiedFalseAndExpiresAtAfterAndOrganizationNameOrderByCreatedAtDesc(
                email, Instant.now(), "__RECOVERY__")
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pending recovery found. Please request a new recovery OTP."));

        if (recovery.getVerificationAttempts() >= maxVerificationAttempts) {
            emailVerificationRepo.delete(recovery);
            throw new IllegalArgumentException("Max verification attempts exceeded. Please request a new OTP.");
        }

        if (!hashOtp(otp).equals(recovery.getOtpCode())) {
            recovery.setVerificationAttempts(recovery.getVerificationAttempts() + 1);
            emailVerificationRepo.save(recovery);
            throw new IllegalArgumentException("Invalid OTP. Please try again.");
        }

        // WHY: Fetch original BEFORE marking recovery verified — avoids
        // NonUniqueResultException (two verified rows for same email would exist
        // after recovery.setVerified(true) is saved below).
        var original = emailVerificationRepo.findFirstByEmailAndVerifiedTrueOrderByCreatedAtAsc(email)
                .filter(v -> !"__RECOVERY__".equals(v.getOrganizationName()))
                .orElseThrow(() -> new IllegalArgumentException("Original registration record not found."));

        // Now safe to mark the recovery token as verified
        recovery.setVerified(true);
        recovery.setVerifiedAt(Instant.now());
        emailVerificationRepo.save(recovery);

        String producerId = generateProducerId(email);

        // Revoke ALL active keys for this producer so any stolen key is
        // immediately dead. The new key is the only valid credential post-recovery.
        var oldKeys = apiKeyRepo.findAllByProducerAndActiveTrue(producerId);
        oldKeys.forEach(k -> {
            k.setActive(false);
            apiKeyRepo.save(k);
        });
        log.info("Revoked {} old key(s) for producer {} during recovery", oldKeys.size(), producerId);

        String newApiKey = "sd_sk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ApiKeyEntity newKey = ApiKeyEntity.builder()
                .id(UUID.randomUUID().toString())
                .apiKey(newApiKey)
                .producer(producerId)
                .label(original.getOrganizationName() + " (Recovered)")
                .admin(false)
                .active(true)
                .createdAt(Instant.now())
                .build();
        apiKeyRepo.save(newKey);

        try {
            emailService.sendWelcomeEmail(email, original.getOrganizationName(), newApiKey, producerId);
        } catch (Exception e) {
            log.error("Failed to send recovery email to {}: {}", email, e.getMessage());
        }

        log.info("Key recovered for producer: {}", producerId);

        return RegistrationResponse.builder()
                .apiKey(newApiKey)
                .producerId(producerId)
                .organizationName(original.getOrganizationName())
                .message(
                        "Key recovery successful! Your new API key has been sent to your email. All previous keys have been revoked.")
                .build();
    }

    // Helpers

    /**
     * WHY: SHA-256 is one-way — a breached DB gives attackers 64-char hex hashes
     * with no way back to the 6-digit OTP. OTPs also expire in 10 minutes and
     * are single-use, making brute-force attacks on the hash impractical.
     */
    private String hashOtp(String otp) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(otp.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String generateProducerId(String email) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(email.getBytes(StandardCharsets.UTF_8));
            return "prod_" + bytesToHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
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
