package com.learnerview.simplydone.config;

import com.learnerview.simplydone.entity.ApiKeyEntity;
import com.learnerview.simplydone.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Initializes the admin API key from ADMIN_INITIAL_SECRET environment variable on application startup.
 * This ensures that the initial admin secret grants access to all admin endpoints.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements ApplicationRunner {

    private final ApiKeyRepository apiKeyRepository;

    @Value("${simplydone.admin.initial-secret:}")
    private String adminInitialSecret;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (adminInitialSecret == null || adminInitialSecret.isBlank()) {
            log.info("ADMIN_INITIAL_SECRET not configured, skipping admin key initialization");
            return;
        }

        // Check if admin key already exists
        var existingAdminKey = apiKeyRepository.findByApiKeyAndActiveTrue(adminInitialSecret);
        if (existingAdminKey.isPresent()) {
            log.info("Admin key already initialized");
            return;
        }

        // Create new admin API key
        ApiKeyEntity adminKey = ApiKeyEntity.builder()
                .id(UUID.randomUUID().toString())
                .apiKey(adminInitialSecret)
                .producer("admin")
                .label("System Administrator")
                .admin(true)
                .active(true)
                .createdAt(Instant.now())
                .build();

        apiKeyRepository.save(adminKey);
        log.info("Admin API key initialized from ADMIN_INITIAL_SECRET");
    }
}
