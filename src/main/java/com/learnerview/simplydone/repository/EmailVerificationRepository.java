package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.entity.EmailVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, String> {
    Optional<EmailVerificationEntity> findFirstByEmailAndVerifiedFalseAndExpiresAtAfterAndOrganizationNameNotOrderByCreatedAtDesc(
        String email, Instant now, String organizationName);

    Optional<EmailVerificationEntity> findFirstByEmailAndVerifiedFalseAndExpiresAtAfterAndOrganizationNameOrderByCreatedAtDesc(
        String email, Instant now, String organizationName);

        long deleteByEmailAndVerifiedFalseAndOrganizationNameNot(String email, String organizationName);

        long deleteByEmailAndVerifiedFalseAndOrganizationName(String email, String organizationName);

    // findFirst avoids NonUniqueResultException when multiple verified rows
    // exist for the same email (e.g. original signup + a completed recovery OTP).
    Optional<EmailVerificationEntity> findFirstByEmailAndVerifiedTrueOrderByCreatedAtAsc(String email);

    void deleteByExpiresAtBefore(Instant now);
}
