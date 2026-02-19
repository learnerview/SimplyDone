package com.learnerview.SimplyDone.dto;

import com.learnerview.SimplyDone.model.Job;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for job submission responses.
 * 
 * This class represents the JSON response returned when a job is successfully submitted.
 * It includes essential job information and timestamps.
 */
@Data
@Builder
@NoArgsConstructor
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
    
}
