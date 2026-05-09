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
    public ResponseEntity<ApiResponse<Void>> requestOtp(@RequestBody SignupRequest request) {
        try {
            registrationService.requestOtp(request.getEmail(), request.getOrganizationName());
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .success(true)
                    .message("OTP sent to your email. Please check your inbox.")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error requesting OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<Void>builder()
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
}
