package com.learnerview.SimplyDone.dto;

import com.learnerview.SimplyDone.model.Job;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

// DTO used to send job info back to the client
@Data
@Builder
public class JobResponse {
    private String id;
    private String message;
    private String jobType;
    private String priority;
    private int delaySeconds;
    private Instant submittedAt;
    private Instant executeAt;
    private Instant executedAt;
    private String status;
    private String userId;
    private int attemptCount;       // how many times this job has been tried so far
    private Integer maxRetries;     // max allowed retries (null means use system default)
    private Map<String, Object> parameters; // job-specific config parameters
    private Object executionResult; // what the job returned when it ran (null if pending/failed)
    private String errorMessage;    // error details if the job failed

    // converts a Job model into this DTO
    public static JobResponse fromJob(Job job) {
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
}
