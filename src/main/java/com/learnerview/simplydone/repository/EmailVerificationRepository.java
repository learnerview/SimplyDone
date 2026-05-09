package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.entity.EmailVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, String> {
    Optional<EmailVerificationEntity> findByEmailAndVerifiedFalseAndExpiresAtAfter(String email, Instant now);
    Optional<EmailVerificationEntity> findByEmailAndVerifiedTrue(String email);
    void deleteByExpiresAtBefore(Instant now);
}
