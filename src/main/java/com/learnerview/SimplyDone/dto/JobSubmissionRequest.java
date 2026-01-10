package com.learnerview.SimplyDone.dto;

import com.learnerview.SimplyDone.model.JobPriority;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

/**
 * Data Transfer Object for job submission requests.
 * 
 * This class represents the JSON payload expected when submitting a new job
 * through the REST API. It includes validation annotations to ensure data integrity.
 */
@Data
public class JobSubmissionRequest {
    
    /**
     * The message or task to be executed
     */
    @NotBlank(message = "Job message cannot be blank")
    private String message;
    
    /**
     * Priority level of the job (HIGH or LOW)
     */
    @NotNull(message = "Job priority cannot be null")
    private JobPriority priority;
    
    /**
     * Delay in seconds before the job should be executed
     */
    @Min(value = 0, message = "Delay must be non-negative")
    @JsonProperty("delay")
    private int delaySeconds;
    
    /**
     * User identifier who is submitting the job
     */
    @NotBlank(message = "User ID cannot be blank")
    private String userId;
}
