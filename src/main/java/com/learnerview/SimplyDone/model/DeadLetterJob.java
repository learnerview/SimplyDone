package com.learnerview.SimplyDone.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a job that has failed and been moved to the dead letter queue.
 * 
 * This class stores the original job along with failure information for
 * later inspection and potential manual retry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterJob {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("originalJob")
    private Job originalJob;
    
    @JsonProperty("failureReason")
    private String failureReason;
    
    @JsonProperty("failureTimestamp")
    private Instant failureTimestamp;
    
    @JsonProperty("retryAttempts")
    private int retryAttempts;
    
    @JsonProperty("originalPriority")
    private JobPriority originalPriority;
    
    @JsonProperty("originalUserId")
    private String originalUserId;
    
    @JsonProperty("canBeRetried")
    @Builder.Default
    private boolean canBeRetried = true;
    
    @JsonProperty("retryCount")
    @Builder.Default
    private int retryCount = 0;
    
    /**
     * Gets the ID of the original job.
     * 
     * @return the original job ID
     */
    public String getOriginalJobId() {
        return originalJob != null ? originalJob.getId() : null;
    }
    
    /**
     * Gets the message of the original job.
     * 
     * @return the original job message
     */
    public String getOriginalMessage() {
        return originalJob != null ? originalJob.getMessage() : null;
    }
    
    /**
     * Increments the retry count for this dead letter job.
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    /**
     * Marks this job as not retryable.
     */
    public void markAsNotRetryable() {
        this.canBeRetried = false;
    }
}
