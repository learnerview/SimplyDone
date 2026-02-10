package com.learnerview.simplydone.service;

import com.learnerview.simplydone.entity.JobEntity;

public interface RetryService {

    void handleFailure(JobEntity job, String errorMessage, long durationMs);

    void logSuccess(JobEntity job, String message, long durationMs);
}
