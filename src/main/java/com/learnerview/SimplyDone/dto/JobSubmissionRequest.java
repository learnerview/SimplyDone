package com.learnerview.SimplyDone.dto;

import com.learnerview.SimplyDone.model.JobPriority;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

// request body for POST /api/jobs
// all fields are validated before the job is saved
@Data
public class JobSubmissionRequest {
    
    // what the job should do
    @NotBlank(message = "Job message cannot be blank")
    private String message;
    
    // HIGH or LOW
    @NotNull(message = "Job priority cannot be null")
    private JobPriority priority;
    
    // seconds to wait before running (0 = run right away)
    @Min(value = 0, message = "Delay must be non-negative")
    @JsonProperty("delay")
    private int delaySeconds;
    
    // who is submitting this (also used for rate limiting)
    @NotBlank(message = "User ID cannot be blank")
    private String userId;

    // which job type to run - determines which executor strategy is used
    @NotNull(message = "Job type cannot be null")
    private com.learnerview.SimplyDone.model.JobType jobType;

    // optional extra data the job needs (email address, file path, api url, etc.)
    private java.util.Map<String, Object> parameters;
}
