package com.learnerview.SimplyDone.dto;

import com.learnerview.SimplyDone.model.Job;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * Data Transfer Object for job submission responses.
 * 
 * This class represents the JSON response returned when a job is successfully submitted.
 * It includes essential job information and timestamps.
 */
@Data
@AllArgsConstructor
public class JobSubmissionResponse {
    
    /**
     * Unique identifier of the submitted job
     */
    private String id;
    
    /**
     * The message or task to be executed
     */
    private String message;
    
    /**
     * Priority level of the job
     */
    private String priority;
    
    /**
     * Delay in seconds before execution
     */
    private int delaySeconds;
    
    /**
     * Timestamp when the job was submitted
     */
    private Instant submittedAt;
    
    /**
     * Timestamp when the job is scheduled for execution
     */
    private Instant executeAt;
    
    /**
     * Status message indicating the result of submission
     */
    private String status;
    
    /**
     * Creates a response from a Job entity.
     * 
     * @param job the job entity to convert
     * @return the response DTO
     */
    public static JobSubmissionResponse fromJob(Job job) {
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
