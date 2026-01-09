package com.learnerview.SimplyDone.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a job in the priority scheduler system.
 * 
 * Jobs are stored in Redis sorted sets with the execution timestamp as the score.
 * This allows for efficient retrieval of jobs that are ready to be executed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {
    
    /**
     * Unique identifier for the job
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    
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
     * Timestamp when the job was submitted
     */
    @Builder.Default
    private Instant submittedAt = Instant.now();
    
    /**
     * Timestamp when the job should be executed
     */
    @NotNull
    private Instant executeAt;
    
    /**
     * User identifier who submitted the job (for rate limiting)
     */
    @NotBlank(message = "User ID cannot be blank")
    private String userId;
    
    /**
     * Timestamp when the job was actually executed (null if not yet executed)
     */
    private Instant executedAt;
    
    /**
     * Status of the job execution
     */
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;
    
    /**
     * Enumeration for job status
     */
    public enum JobStatus {
        PENDING,    // Job is waiting to be executed
        EXECUTED,   // Job has been successfully executed
        FAILED      // Job execution failed
    }
    
    /**
     * Checks if the job is ready for execution based on the current time.
     * 
     * @return true if the job's execution time has arrived, false otherwise
     */
    public boolean isReadyForExecution() {
        return Instant.now().isAfter(executeAt) || Instant.now().equals(executeAt);
    }
    
    /**
     * Marks the job as executed with the current timestamp.
     */
    public void markAsExecuted() {
        this.executedAt = Instant.now();
        this.status = JobStatus.EXECUTED;
    }
    
    /**
     * Marks the job as failed.
     */
    public void markAsFailed() {
        this.executedAt = Instant.now();
        this.status = JobStatus.FAILED;
    }
}
