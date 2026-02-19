package com.learnerview.SimplyDone.dto;

import com.learnerview.SimplyDone.model.Job;

// centralizes all Job <-> DTO mapping in one place (Rule 9)
// controllers and services use this instead of scattering fromJob() across DTOs
public final class JobMapper {

    private JobMapper() {}

    public static JobResponse toJobResponse(Job job) {
        if (job == null) return null;
        return JobResponse.builder()
                .id(job.getId())
                .message(job.getMessage())
                .jobType(job.getJobType() != null ? job.getJobType().name() : null)
                .priority(job.getPriority().name())
                .delaySeconds(job.getDelaySeconds())
                .submittedAt(job.getSubmittedAt())
                .executeAt(job.getExecuteAt())
                .executedAt(job.getExecutedAt())
                .status(job.getStatus().name())
                .userId(job.getUserId())
                .attemptCount(job.getAttemptCount())
                .maxRetries(job.getMaxRetries())
                .parameters(job.getParameters())
                .executionResult(job.getExecutionResult())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    public static JobSubmissionResponse toSubmissionResponse(Job job) {
        return new JobSubmissionResponse(
            job.getId(),
            job.getMessage(),
            job.getPriority().name(),
            job.getDelaySeconds(),
            job.getSubmittedAt(),
            job.getExecuteAt(),
            "Job submitted successfully"
        );
    }
}
