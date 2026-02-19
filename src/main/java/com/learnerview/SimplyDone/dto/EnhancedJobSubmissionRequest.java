package com.learnerview.SimplyDone.dto;

import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

// request body for the enhanced job submission endpoint
// same as JobSubmissionRequest but with extra fields for scheduling, retries, timeouts and dependencies
@Data
public class EnhancedJobSubmissionRequest {

    // the type of job to run (EMAIL_SEND, DATA_PROCESS, etc.)
    @NotNull(message = "Job type cannot be null")
    private JobType jobType;

    // what the job should do - used as a description
    @NotBlank(message = "Job message cannot be blank")
    private String message;

    // HIGH or LOW - HIGH queue gets checked first
    @NotNull(message = "Job priority cannot be null")
    private JobPriority priority;

    // how many seconds to wait before running - ignored if scheduledAtEpochSeconds is set
    @Min(value = 0, message = "Delay must be non-negative")
    @JsonProperty("delay")
    private int delaySeconds;

    // who is submitting this job
    @NotBlank(message = "User ID cannot be blank")
    private String userId;

    // any extra data the job needs to run (e.g. email address, file path, api url)
    private java.util.Map<String, Object> parameters;

    // unix timestamp (epoch seconds) for when to run the job - takes priority over delaySeconds
    private Long scheduledAtEpochSeconds;

    // max time the job is allowed to run before being killed (default is 300s = 5min)
    @Min(value = 1, message = "Timeout must be at least 1 second")
    private Integer timeoutSeconds;

    // how many times to retry if the job fails (null uses the system default)
    @Min(value = 0, message = "Max retries cannot be negative")
    private Integer maxRetries;

    // list of job IDs that must finish before this one can start
    private String[] dependencies;
}
