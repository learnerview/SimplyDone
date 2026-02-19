package com.learnerview.SimplyDone.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.SimplyDone.service.RetryService;
import com.learnerview.SimplyDone.model.DeadLetterJob;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implementation of RetryService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RetryServiceImpl implements RetryService {

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    @Value("${simplydone.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${simplydone.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${simplydone.retry.initial-delay-seconds:5}")
    private int initialDelaySeconds;

    @Override
    public boolean retryJob(Job job, Exception error) {
        String jobId = job.getId();
        
        // Use job's attemptCount instead of ConcurrentHashMap
        int currentAttempts = job.getAttemptCount() + 1;
        
        // Use job-specific maxRetries if set, otherwise use global config
        int maxAttempts = (job.getMaxRetries() != null && job.getMaxRetries() > 0) 
                ? job.getMaxRetries() 
                : maxRetryAttempts;

        log.info("Attempting retry {} for job {} (max: {})", currentAttempts, jobId, maxAttempts);

        if (currentAttempts > maxAttempts) {
            log.warn("Job {} exceeded max retry attempts ({}/{})", jobId, currentAttempts - 1, maxAttempts);
            moveToDeadLetterQueue(job, error);
            return false;
        }

        long delaySeconds = calculateBackoffDelay(currentAttempts);
        Instant retryAt = Instant.now().plusSeconds(delaySeconds);

        // Retry with the same job ID so tracking and audit trail are preserved
        Job retryJob = Job.builder()
                .id(job.getId())
                .jobType(job.getJobType())
                .userId(job.getUserId())
                .priority(job.getPriority())
                .parameters(job.getParameters())
                .maxRetries(job.getMaxRetries())
                .timeoutSeconds(job.getTimeoutSeconds())
                .message(job.getMessage())
                .attemptCount(currentAttempts)  // Track retry attempt count
                .executeAt(retryAt)
                .build();

        try {
            jobRepository.saveJob(retryJob);
            log.info("Scheduled retry for job {} at {} (attempt {}/{})", jobId, retryAt, currentAttempts, maxAttempts);
            return true;
        } catch (Exception e) {
            log.error("Failed to schedule retry for job {}: {}", jobId, e.getMessage());
            return false;
        }
    }

    private long calculateBackoffDelay(int attempt) {
        // Pure exponential backoff — no cap, fully driven by configuration:
        // attempt 1 → initialDelaySeconds * multiplier^0 = initialDelaySeconds
        // attempt 2 → initialDelaySeconds * multiplier^1
        // attempt N → initialDelaySeconds * multiplier^(N-1)
        return (long) (initialDelaySeconds * Math.pow(backoffMultiplier, attempt - 1));
    }

    private void moveToDeadLetterQueue(Job job, Exception error) {
        try {
            DeadLetterJob deadLetterJob = new DeadLetterJob();
            deadLetterJob.setOriginalJob(job);
            deadLetterJob.setFailureReason(error.getMessage());
            deadLetterJob.setFailureTimestamp(Instant.now());
            deadLetterJob.setRetryAttempts(job.getAttemptCount());
            deadLetterJob.setOriginalPriority(job.getPriority());
            deadLetterJob.setOriginalUserId(job.getUserId());
            
            String deadLetterJson = objectMapper.writeValueAsString(deadLetterJob);
            jobRepository.saveToDeadLetterQueue(deadLetterJson);
            log.info("Moved job {} to dead-letter queue after {} attempts", job.getId(), job.getAttemptCount());
        } catch (Exception e) {
            log.error("Failed to move job {} to DLQ: {}", job.getId(), e.getMessage());
        }
    }

    @Override
    public void resetRetryAttempts(String jobId) {
        // No-op: attemptCount is now tracked on the Job object itself
        // This method is kept for interface compatibility
        log.debug("resetRetryAttempts called for job {} (no action needed - using job.attemptCount)", jobId);
    }

    @Override
    public RetryStatistics getRetryStatistics() {
        // Since we removed the ConcurrentHashMap, return configured max attempts
        // Individual job retry counts are now stored in the job.attemptCount field
        return new RetryStatistics(0, 0, maxRetryAttempts);
    }

    @Override
    public List<DeadLetterJob> getDeadLetterJobs() {
        try {
            Set<String> deadLetterJobsRaw = jobRepository.getDeadLetterJobsRaw();
            List<DeadLetterJob> jobs = new ArrayList<>();
            if (deadLetterJobsRaw != null) {
                for (String jobJson : deadLetterJobsRaw) {
                    jobs.add(objectMapper.readValue(jobJson, DeadLetterJob.class));
                }
            }
            return jobs;
        } catch (Exception e) {
            log.error("Failed to retrieve DLQ jobs", e);
            return new ArrayList<>();
        }
    }

    @Override
    public int clearDeadLetterQueue() {
        return jobRepository.clearDeadLetterQueue();
    }

    @Override
    public boolean retryDeadLetterJob(String deadLetterJobId) {
        try {
            Set<String> deadLetterJobsRaw = jobRepository.getDeadLetterJobsRaw();
            if (deadLetterJobsRaw != null) {
                for (String jobJson : deadLetterJobsRaw) {
                    DeadLetterJob deadLetterJob = objectMapper.readValue(jobJson, DeadLetterJob.class);
                    String originalJobId = deadLetterJob.getOriginalJobId();
                    if (originalJobId != null && originalJobId.equals(deadLetterJobId)) {
                        jobRepository.removeFromDeadLetterQueue(jobJson);
                        Job originalJob = deadLetterJob.getOriginalJob();

                        // Re-queue with original ID so audit trail is preserved
                        Job retryJob = Job.builder()
                                .id(originalJob.getId())
                                .jobType(originalJob.getJobType())
                                .userId(originalJob.getUserId())
                                .priority(originalJob.getPriority())
                                .parameters(originalJob.getParameters())
                                .maxRetries(originalJob.getMaxRetries())
                                .timeoutSeconds(originalJob.getTimeoutSeconds())
                                .message(originalJob.getMessage())
                                .attemptCount(0)  // Reset attempt count for DLQ retry
                                .executeAt(Instant.now())
                                .build();

                        jobRepository.saveJob(retryJob);
                        log.info("Retried DLQ job {} as new job {}", deadLetterJobId, retryJob.getId());
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to retry DLQ job {}", deadLetterJobId, e);
            return false;
        }
    }
}
