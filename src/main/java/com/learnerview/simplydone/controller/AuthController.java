package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.dto.ApiResponse;
import com.learnerview.simplydone.dto.OtpVerificationRequest;
import com.learnerview.simplydone.dto.RegistrationResponse;
import com.learnerview.simplydone.dto.SignupRequest;
import com.learnerview.simplydone.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final RegistrationService registrationService;

    /**
     * Request OTP for email verification.
     * This initiates the self-service registration flow.
     */
    @PostMapping("/signup/request-otp")
        public ResponseEntity<ApiResponse<RegistrationResponse>> requestOtp(@RequestBody SignupRequest request) {
        try {
            RegistrationResponse response = registrationService.requestOtp(request.getEmail(), request.getOrganizationName());
            ApiResponse.ApiResponseBuilder<RegistrationResponse> builder = ApiResponse.<RegistrationResponse>builder()
                .success(true);
            if (response != null) {
            return ResponseEntity.ok(builder
                .message(response.getMessage())
                .data(response)
                .build());
            }
            return ResponseEntity.ok(builder
                .message("OTP sent to your email. Please check your inbox.")
                .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<RegistrationResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error requesting OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<RegistrationResponse>builder()
                    .success(false)
                    .message("Failed to send OTP. Please try again later.")
                    .build());
        }
    }

    /**
     * Verify OTP and create API key.
     * Returns the API credentials upon successful verification.
     */
    @PostMapping("/signup/verify-otp")
    public ResponseEntity<ApiResponse<RegistrationResponse>> verifyOtpAndCreateKey(
            @RequestBody OtpVerificationRequest request) {
        try {
            RegistrationResponse response = registrationService.verifyOtpAndCreateKey(request.getEmail(), request.getOtp());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<RegistrationResponse>builder()
                    .success(true)
                    .message("Registration successful!")
                    .data(response)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<RegistrationResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error verifying OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<RegistrationResponse>builder()
                    .success(false)
                    .message("Failed to verify OTP. Please try again later.")
                    .build());
        }
    }

    /**
     * Step 1 of key recovery: send an OTP to the registered email.
     * Only works for emails that have already been verified (registered users).
     */
    @PostMapping("/recover/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestRecoveryOtp(@RequestBody SignupRequest request) {
        try {
            registrationService.requestRecoveryOtp(request.getEmail());
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .success(true)
                    .message("Recovery OTP sent to your email.")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error requesting recovery OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<Void>builder()
                    .success(false)
                    .message("Failed to send recovery OTP. Please try again later.")
                    .build());
        }
    }

    /**
     * Step 2 of key recovery: verify OTP, revoke old keys, and issue a new one.
     * The new API key is returned in the response AND emailed to the user.
     */
    @PostMapping("/recover/verify-otp")
    public ResponseEntity<ApiResponse<RegistrationResponse>> recoverKey(
            @RequestBody OtpVerificationRequest request) {
        try {
            RegistrationResponse response = registrationService.recoverKey(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(ApiResponse.<RegistrationResponse>builder()
                    .success(true)
                    .message("Key recovery successful! All previous keys have been revoked.")
                    .data(response)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<RegistrationResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error recovering key: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<RegistrationResponse>builder()
                    .success(false)
                    .message("Failed to recover key. Please try again later.")
                    .build());
        }
    }
}
