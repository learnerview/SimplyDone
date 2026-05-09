package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.entity.AppSettingEntity;
import com.learnerview.simplydone.repository.AppSettingRepository;
import com.learnerview.simplydone.service.EmailVerificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationSettingsServiceImpl implements EmailVerificationSettingsService {

    private static final String EMAIL_VERIFICATION_ENABLED = "email_verification_enabled";

    private final AppSettingRepository appSettingRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailVerificationEnabled() {
        return appSettingRepository.findById(EMAIL_VERIFICATION_ENABLED)
                .map(AppSettingEntity::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(true);
    }

    @Override
    public void setEmailVerificationEnabled(boolean enabled) {
        Instant now = Instant.now();
        AppSettingEntity entity = appSettingRepository.findById(EMAIL_VERIFICATION_ENABLED)
                .map(existing -> {
                    existing.setSettingValue(Boolean.toString(enabled));
                    existing.setUpdatedAt(now);
                    return existing;
                })
                .orElseGet(() -> AppSettingEntity.builder()
                        .settingKey(EMAIL_VERIFICATION_ENABLED)
                        .settingValue(Boolean.toString(enabled))
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
        appSettingRepository.save(entity);
    }
}