package com.learnerview.simplydone.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "email_verifications")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmailVerificationEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 255, nullable = false)
    private String email;

    @Column(length = 6, nullable = false)
    private String otpCode;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private int verificationAttempts;

    @Column(length = 255)
    private String organizationName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant verifiedAt;
}
