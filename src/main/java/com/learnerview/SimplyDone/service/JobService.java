package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobSubmissionResponse;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.repository.JobRepository;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service layer for job management operations.
 * 
 * This service handles the core business logic for job submission,
 * including validation, execution time calculation, and persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {
    
    private final JobRepository jobRepository;
    private RetryService retryService;
    private final Timer jobExecutionTimer;
    private final Timer jobSubmissionTimer;
    
    // Use setter injection to break circular dependency
    public void setRetryService(RetryService retryService) {
        this.retryService = retryService;
    }
    
    /**
     * Submits a new job to the scheduler.
     * 
     * @param request the job submission request
     * @return response containing job details
     * @throws IllegalArgumentException if request is invalid
     */
    public JobSubmissionResponse submitJob(JobSubmissionRequest request) {
        return jobSubmissionTimer.record(() -> {
            log.info("Submitting job for user: {} with priority: {} and delay: {}s", 
                    request.getUserId(), request.getPriority(), request.getDelaySeconds());
            
            // Calculate execution time
            Instant executeAt = Instant.now().plusSeconds(request.getDelaySeconds());
            
            // Create job entity
            Job job = Job.builder()
                    .message(request.getMessage())
                    .priority(request.getPriority())
                    .delaySeconds(request.getDelaySeconds())
                    .userId(request.getUserId())
                    .executeAt(executeAt)
                    .build();
            
            // Save job to Redis
            boolean saved = jobRepository.saveJob(job);
            
            if (!saved) {
                log.error("Failed to save job {} to Redis", job.getId());
                throw new RuntimeException("Failed to save job to storage");
            }
            
            log.info("Job {} submitted successfully, scheduled for execution at {}", 
                    job.getId(), executeAt);
            
            return JobSubmissionResponse.fromJob(job);
        });
    }
    
    /**
     * Retrieves and executes the next ready job.
     * 
     * @return true if a job was executed, false if no jobs were ready
     */
    public boolean executeNextReadyJob() {
        return jobExecutionTimer.record(() -> {
            try {
                Job job = jobRepository.getNextReadyJob();
                
                if (job == null) {
                    return false;
                }
                
                // Execute the job with retry logic
                boolean success = executeJobWithRetry(job);
                
                if (success) {
                    // Mark as executed and update statistics
                    job.markAsExecuted();
                    jobRepository.incrementExecutedJobsCounter();
                    retryService.resetRetryAttempts(job.getId());
                    
                    log.info("Job {} executed successfully: {}", job.getId(), job.getMessage());
                } else {
                    log.warn("Job {} execution failed, retry logic handled", job.getId());
                }
                
                return success;
                
            } catch (Exception e) {
                // Let Redis connection failures bubble up to the worker
                if (e.getCause() instanceof org.springframework.dao.DataAccessException ||
                    e.getMessage().contains("Unable to connect to Redis")) {
                    throw new org.springframework.dao.DataAccessException("Redis connection failed", e) {};
                }
                
                log.error("Failed to execute job: {}", e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Executes a job with retry logic.
     * 
     * @param job the job to execute
     * @return true if successful, false if failed (retry logic will handle)
     */
    private boolean executeJobWithRetry(Job job) {
        try {
            executeJob(job);
            return true;
        } catch (Exception e) {
            log.error("Job {} execution failed: {}", job.getId(), e.getMessage());
            
            // Attempt retry
            boolean retryScheduled = retryService.retryJob(job, e);
            if (!retryScheduled) {
                // Max retries exceeded, increment rejected counter
                jobRepository.incrementRejectedJobsCounter();
            }
            
            return false;
        }
    }
    
    /**
     * Executes a job by processing its message.
     * 
     * In a real-world scenario, this would contain the actual business logic
     * for job execution. For this implementation, we simply log the message.
     * 
     * @param job the job to execute
     */
    private void executeJob(Job job) {
        log.info("🚀 EXECUTING JOB [{}] - Priority: {}, User: {}, Message: {}", 
                job.getId(), job.getPriority(), job.getUserId(), job.getMessage());
        
        // Simulate job execution time
        try {
            Thread.sleep(100); // 100ms execution time for demonstration
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job execution interrupted", e);
        }
    }
    
    /**
     * Cancels a job by removing it from the queue.
     * 
     * @param jobId the ID of the job to cancel
     * @return true if job was found and cancelled, false if not found
     */
    public boolean cancelJob(String jobId) {
        log.info("Attempting to cancel job: {}", jobId);
        
        try {
            boolean cancelled = jobRepository.cancelJob(jobId);
            
            if (cancelled) {
                log.info("Job {} cancelled successfully", jobId);
            } else {
                log.warn("Job {} not found or could not be cancelled", jobId);
            }
            
            return cancelled;
            
        } catch (Exception e) {
            log.error("Failed to cancel job {}: {}", jobId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets a specific job by ID.
     * 
     * @param jobId the ID of the job to retrieve
     * @return the job if found, null otherwise
     */
    public Job getJobById(String jobId) {
        try {
            return jobRepository.getJobById(jobId);
        } catch (Exception e) {
            log.error("Failed to retrieve job {}: {}", jobId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the current queue sizes for statistics.
     * 
     * @return array with [highPriorityCount, lowPriorityCount]
     */
    public long[] getQueueSizes() {
        long highPriorityCount = jobRepository.getQueueSize(JobPriority.HIGH);
        long lowPriorityCount = jobRepository.getQueueSize(JobPriority.LOW);
        
        return new long[]{highPriorityCount, lowPriorityCount};
    }
}
