package com.learnerview.SimplyDone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import com.learnerview.SimplyDone.model.DeadLetterJob;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for handling job retry logic.
 * 
 * This service implements retry patterns inspired by the redis-scheduler-master
 * and rate-limiter projects, providing:
 * - Configurable retry attempts
 * - Exponential backoff
 * - Dead letter queue for failed jobs
 * - Retry statistics
 */
@Service
@Slf4j
public class RetryService {

    private final JobRepository jobRepository;
    private JobService jobService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${simplydone.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${simplydone.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${simplydone.retry.initial-delay-seconds:5}")
    private int initialDelaySeconds;

    // Use setter injection to break circular dependency
    public RetryService(JobRepository jobRepository, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    // Track retry attempts for each job
    private final ConcurrentHashMap<String, AtomicInteger> retryAttempts = new ConcurrentHashMap<>();

    /**
     * Attempts to retry a failed job.
     * 
     * @param job the job that failed
     * @param error the error that caused the failure
     * @return true if job was scheduled for retry, false if max retries exceeded
     */
    public boolean retryJob(Job job, Exception error) {
        String jobId = job.getId();
        AtomicInteger attempts = retryAttempts.computeIfAbsent(jobId, k -> new AtomicInteger(0));
        int currentAttempts = attempts.incrementAndGet();

        log.info("Attempting retry {} for job {} (max: {})", currentAttempts, jobId, maxRetryAttempts);

        if (currentAttempts > maxRetryAttempts) {
            log.warn("Job {} exceeded maximum retry attempts ({}), moving to dead letter queue", 
                    jobId, maxRetryAttempts);
            moveToDeadLetterQueue(job, error);
            retryAttempts.remove(jobId);
            return false;
        }

        // Calculate delay with exponential backoff
        long delaySeconds = calculateBackoffDelay(currentAttempts);
        Instant retryAt = Instant.now().plusSeconds(delaySeconds);

        // Create retry job submission request
        JobSubmissionRequest retryRequest = new JobSubmissionRequest();
        retryRequest.setMessage(job.getMessage());
        retryRequest.setPriority(job.getPriority());
        retryRequest.setUserId(job.getUserId());
        retryRequest.setDelaySeconds((int) (retryAt.getEpochSecond() - Instant.now().getEpochSecond()));

        // Schedule the retry
        try {
            jobService.submitJob(retryRequest);
            log.info("Job {} scheduled for retry at {} (attempt {})", jobId, retryAt, currentAttempts);
            return true;
        } catch (Exception e) {
            log.error("Failed to schedule retry for job {}: {}", jobId, e.getMessage());
            return false;
        }
    }

    /**
     * Calculates exponential backoff delay.
     * 
     * @param attempt the current attempt number (1-based)
     * @return delay in seconds
     */
    private long calculateBackoffDelay(int attempt) {
        // Exponential backoff: initialDelay * (multiplier ^ (attempt - 1))
        double delay = initialDelaySeconds * Math.pow(backoffMultiplier, attempt - 1);
        
        // Cap the delay to prevent excessive delays
        long maxDelay = 300; // 5 minutes max
        return Math.min((long) delay, maxDelay);
    }

    /**
     * Moves a job to the dead letter queue after max retries exceeded.
     * 
     * @param job the failed job
     * @param error the error that caused the failure
     */
    private void moveToDeadLetterQueue(Job job, Exception error) {
        try {
            // Create dead letter job with error information
            DeadLetterJob deadLetterJob = new DeadLetterJob();
            deadLetterJob.setOriginalJob(job);
            deadLetterJob.setFailureReason(error.getMessage());
            deadLetterJob.setFailureTimestamp(Instant.now());
            deadLetterJob.setRetryAttempts(retryAttempts.get(job.getId()).get());
            deadLetterJob.setOriginalPriority(job.getPriority());
            deadLetterJob.setOriginalUserId(job.getUserId());
            
            // Store in Redis dead letter queue
            String deadLetterKey = "dead_letter:jobs";
            String deadLetterJson = objectMapper.writeValueAsString(deadLetterJob);
            
            // Use timestamp as score for chronological ordering
            double score = Instant.now().toEpochMilli();
            redisTemplate.opsForZSet().add(deadLetterKey, deadLetterJson, score);
            
            log.warn("Job {} moved to dead letter queue - Reason: {}, Attempts: {}", 
                    job.getId(), error.getMessage(), deadLetterJob.getRetryAttempts());
            
        } catch (Exception e) {
            log.error("Failed to move job {} to dead letter queue: {}", job.getId(), e.getMessage());
        }
    }

    /**
     * Resets retry attempts for a job (useful when job succeeds).
     * 
     * @param jobId the job ID
     */
    public void resetRetryAttempts(String jobId) {
        retryAttempts.remove(jobId);
        log.debug("Reset retry attempts for job {}", jobId);
    }

    /**
     * Gets retry statistics.
     * 
     * @return retry statistics
     */
    public RetryStatistics getRetryStatistics() {
        int totalRetryingJobs = retryAttempts.size();
        int totalAttempts = retryAttempts.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        return new RetryStatistics(totalRetryingJobs, totalAttempts, maxRetryAttempts);
    }

    /**
     * Gets all jobs in the dead letter queue.
     * 
     * @return list of dead letter jobs
     */
    public List<DeadLetterJob> getDeadLetterJobs() {
        try {
            String deadLetterKey = "dead_letter:jobs";
            Set<String> deadLetterJobs = redisTemplate.opsForZSet().range(deadLetterKey, 0, -1);
            
            List<DeadLetterJob> jobs = new ArrayList<>();
            if (deadLetterJobs != null) {
                for (String jobJson : deadLetterJobs) {
                    try {
                        DeadLetterJob job = objectMapper.readValue(jobJson, DeadLetterJob.class);
                        jobs.add(job);
                    } catch (Exception e) {
                        log.error("Failed to parse dead letter job JSON: {}", e.getMessage());
                    }
                }
            }
            return jobs;
        } catch (Exception e) {
            log.error("Failed to retrieve dead letter jobs: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Clears the dead letter queue.
     * 
     * @return number of jobs cleared
     */
    public int clearDeadLetterQueue() {
        try {
            String deadLetterKey = "dead_letter:jobs";
            Long count = redisTemplate.opsForZSet().size(deadLetterKey);
            if (count != null && count > 0) {
                redisTemplate.delete(deadLetterKey);
                log.info("Cleared {} jobs from dead letter queue", count);
                return count.intValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("Failed to clear dead letter queue: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Retries a job from the dead letter queue.
     * 
     * @param deadLetterJobId the ID of the dead letter job to retry
     * @return true if retry was successful, false otherwise
     */
    public boolean retryDeadLetterJob(String deadLetterJobId) {
        try {
            String deadLetterKey = "dead_letter:jobs";
            Set<String> deadLetterJobs = redisTemplate.opsForZSet().range(deadLetterKey, 0, -1);
            
            if (deadLetterJobs != null) {
                for (String jobJson : deadLetterJobs) {
                    try {
                        DeadLetterJob deadLetterJob = objectMapper.readValue(jobJson, DeadLetterJob.class);
                        if (deadLetterJob.getOriginalJobId().equals(deadLetterJobId)) {
                            
                            // Remove from dead letter queue
                            redisTemplate.opsForZSet().remove(deadLetterKey, jobJson);
                            
                            // Reset retry attempts and resubmit
                            Job originalJob = deadLetterJob.getOriginalJob();
                            retryAttempts.remove(originalJob.getId());
                            
                            JobSubmissionRequest retryRequest = new JobSubmissionRequest();
                            retryRequest.setMessage(originalJob.getMessage());
                            retryRequest.setPriority(originalJob.getPriority());
                            retryRequest.setUserId(originalJob.getUserId());
                            retryRequest.setDelaySeconds(0);
                            
                            jobService.submitJob(retryRequest);
                            
                            log.info("Successfully retried dead letter job: {}", deadLetterJobId);
                            return true;
                        }
                    } catch (Exception e) {
                        log.error("Failed to process dead letter job: {}", e.getMessage());
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to retry dead letter job {}: {}", deadLetterJobId, e.getMessage());
            return false;
        }
    }
    
    /**
     * DTO for retry statistics.
     */
    public record RetryStatistics(
            int retryingJobs,
            int totalAttempts,
            int maxAttempts
    ) {}
}
