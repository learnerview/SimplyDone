package com.learnerview.simplydone.service;

public interface EmailVerificationSettingsService {
    boolean isEmailVerificationEnabled();

    void setEmailVerificationEnabled(boolean enabled);
}