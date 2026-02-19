package com.learnerview.SimplyDone.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.util.Map;

/**
 * Enhanced job request with support for different job types and parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRequest {
    
    @NotBlank(message = "Job type cannot be blank")
    private JobType jobType;
    
    @NotBlank(message = "Job message cannot be blank")
    private String message;
    
    @NotNull(message = "Job priority cannot be null")
    private JobPriority priority;
    
    @Min(value = 0, message = "Delay must be non-negative")
    @JsonProperty("delay")
    private int delaySeconds;
    
    @NotBlank(message = "User ID cannot be blank")
    private String userId;
    
    /**
     * Job-specific parameters (e.g., email recipients, API endpoints, file paths)
     */
    private Map<String, Object> parameters;
    
    /**
     * Maximum execution time in seconds (default: 300 seconds = 5 minutes)
     */
    @Builder.Default
    private int timeoutSeconds = 300;
    
    /**
     * Number of retry attempts for this specific job (overrides global setting)
     */
    private Integer maxRetries;
    
    /**
     * Whether this job has dependencies that must complete first
     */
    private String[] dependencies;
}
