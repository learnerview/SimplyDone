package com.learnerview.SimplyDone.service.impl;

import com.learnerview.SimplyDone.dto.EnhancedJobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobMapper;
import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobSubmissionResponse;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.entity.JobEntity;
import com.learnerview.SimplyDone.exception.RateLimitException;
import com.learnerview.SimplyDone.exception.ValidationException;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobStatus;
import com.learnerview.SimplyDone.model.JobType;
import com.learnerview.SimplyDone.repository.JobEntityRepository;
import com.learnerview.SimplyDone.repository.JobRepository;
import com.learnerview.SimplyDone.service.JobService;
import com.learnerview.SimplyDone.service.RateLimitingService;
import com.learnerview.SimplyDone.service.RetryService;
import com.learnerview.SimplyDone.service.JobExecutor;
import com.learnerview.SimplyDone.service.JobExecutorFactory;
import com.learnerview.SimplyDone.service.strategy.JobExecutionStrategy;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of JobService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final RateLimitingService rateLimitingService;
    private final RetryService retryService;
    private final JobExecutor jobExecutor;
    private final Timer jobExecutionTimer;
    private final JobExecutorFactory jobExecutorFactory;

    // optional PostgreSQL persistence – absent in unit tests, injected in production
    @Autowired(required = false)
    private JobEntityRepository jobEntityRepository;

    @Override
    public JobSubmissionResponse submitJob(JobSubmissionRequest request) {
        String jobId = UUID.randomUUID().toString();

        Job job = Job.builder()
            .id(jobId)
            .jobType(request.getJobType())
            .userId(request.getUserId())
            .message(request.getMessage())
            .parameters(request.getParameters())
            .priority(request.getPriority() != null ? request.getPriority() : JobPriority.LOW)
            .executeAt(request.getDelaySeconds() != 0 ?
                Instant.now().plusSeconds(request.getDelaySeconds()) :
                Instant.now())
            .build();

        jobRepository.saveJob(job);
        persistJobEntity(job);
        log.info("Job submitted: {} (type: {}, priority: {})", jobId, job.getJobType(), job.getPriority());

        return JobMapper.toSubmissionResponse(job);
    }

    @Override
    public boolean executeNextReadyJob() {
        return jobExecutionTimer.record(() -> {
            // Check high priority queue first
            Job job = jobRepository.getNextReadyJob(JobPriority.HIGH);
            
            // If no high priority, check low priority
            if (job == null) {
                job = jobRepository.getNextReadyJob(JobPriority.LOW);
            }
            
            if (job != null) {
                try {
                    log.info("Executing job {}: {}", job.getId(), job.getMessage());
                    
                    // Actually execute the job task via the executor
                    jobExecutor.execute(job);
                    
                    // Mark job as successfully executed
                    job.setStatus(com.learnerview.SimplyDone.model.JobStatus.EXECUTED);
                    job.setExecutedAt(Instant.now());
                    
                    jobRepository.incrementExecutedJobsCounter();
                    jobRepository.updateJobStatus(job); // Persist EXECUTED status
                    persistJobEntity(job);
                    retryService.resetRetryAttempts(job.getId());
                    return true;
                } catch (Exception e) {
                    log.error("Failed to execute job {}: {}", job.getId(), e.getMessage());
                    
                    // Mark job as failed before retry handling
                    job.setStatus(com.learnerview.SimplyDone.model.JobStatus.FAILED);
                    job.setErrorMessage(e.getMessage());
                    
                    retryService.retryJob(job, e);
                    jobRepository.updateJobStatus(job); // Persist FAILED (or retrying) status
                    persistJobEntity(job);
                    return false;
                }
            }
            
            return false;
        });
    }

    @Override
    public Job getJobById(String jobId) {
        return jobRepository.getJobById(jobId);
    }

    @Override
    public boolean cancelJob(String jobId) {
        return jobRepository.deleteJob(jobId);
    }

    @Override
    public long[] getQueueSizes() {
        return new long[] {
            jobRepository.getQueueSize(JobPriority.HIGH),
            jobRepository.getQueueSize(JobPriority.LOW)
        };
    }
    
    @Override
    public long estimateExecutionTime(Job job) {
        return jobExecutor.estimateExecutionTime(job);
    }
    
    @Override
    public JobExecutionStrategy.ResourceRequirements getResourceRequirements(Job job) {
        return jobExecutor.getResourceRequirements(job);
    }
    
    @Override
    public boolean canExecute(Job job) {
        return jobExecutor.canExecute(job);
    }

    // builds a Job from the enhanced request fields and saves it the same way submitJob does
    @Override
    public JobSubmissionResponse submitEnhancedJob(EnhancedJobSubmissionRequest request) {
        validateEnhancedJobRequest(request);

        String jobId = UUID.randomUUID().toString();

        // use the explicit epoch timestamp if given, otherwise fall back to delay
        Instant executeAt;
        if (request.getScheduledAtEpochSeconds() != null) {
            executeAt = Instant.ofEpochSecond(request.getScheduledAtEpochSeconds());
        } else {
            executeAt = request.getDelaySeconds() != 0
                    ? Instant.now().plusSeconds(request.getDelaySeconds())
                    : Instant.now();
        }

        Job job = Job.builder()
                .id(jobId)
                .jobType(request.getJobType())
                .message(request.getMessage())
                .userId(request.getUserId())
                .parameters(request.getParameters())
                .priority(request.getPriority() != null ? request.getPriority() : JobPriority.LOW)
                .delaySeconds(request.getDelaySeconds())
                .executeAt(executeAt)
                .maxRetries(request.getMaxRetries())
                .timeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 300)
                .dependencies(request.getDependencies())
                .build();

        jobRepository.saveJob(job);
        persistJobEntity(job);
        log.info("Enhanced job submitted: {} (type: {}, priority: {})", jobId, job.getJobType(), job.getPriority());
        return JobMapper.toSubmissionResponse(job);
    }

    // just returns a placeholder for now - batch tracking requires a separate store
    @Override
    public Object getBatchStatus(String batchId) {
        return Map.of(
                "batchId", batchId,
                "status", "UNKNOWN",
                "message", "Batch tracking not yet implemented"
        );
    }

    // for now this is a no-op since we don't have a batch tracking table yet
    @Override
    public void cancelBatch(String batchId) {
        log.warn("Batch cancel requested for batchId: {} - not yet implemented", batchId);
    }

    // saves job to PostgreSQL for durable storage and historical queries
    // non-critical: failures are logged but do not abort the request
    private void persistJobEntity(Job job) {
        if (jobEntityRepository == null) return;
        try {
            JobEntity entity = JobEntity.builder()
                    .id(job.getId())
                    .jobType(job.getJobType())
                    .message(job.getMessage())
                    .priority(job.getPriority())
                    .delaySeconds(job.getDelaySeconds())
                    .userId(job.getUserId())
                    .timeoutSeconds(job.getTimeoutSeconds())
                    .maxRetries(job.getMaxRetries())
                    .submittedAt(job.getSubmittedAt())
                    .executeAt(job.getExecuteAt())
                    .executedAt(job.getExecutedAt())
                    .status(job.getStatus() != null ? job.getStatus() : JobStatus.PENDING)
                    .attemptCount(job.getAttemptCount())
                    .errorMessage(job.getErrorMessage())
                    .build();
            jobEntityRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist job {} to PostgreSQL: {}", job.getId(), e.getMessage());
        }
    }

    private void enforceRateLimit(String userId) {
        boolean allowed = rateLimitingService.isAllowed(userId);
        if (!allowed) {
            RateLimitStatus status = rateLimitingService.getRateLimitStatus(userId);
            log.warn("Rate limit exceeded for user: {} ({}/{})", userId, status.currentCount(), status.maxRequests());
            throw new RateLimitException(
                "Maximum " + status.maxRequests() + " jobs per minute exceeded",
                status.resetTimeSeconds()
            );
        }
    }

    private void validateEnhancedJobRequest(EnhancedJobSubmissionRequest request) {
        Map<String, String> errors = new HashMap<>();

        if (request.getScheduledAtEpochSeconds() != null &&
                Instant.ofEpochSecond(request.getScheduledAtEpochSeconds()).isBefore(Instant.now())) {
            errors.put("scheduledAtEpochSeconds", "Schedule time must be in the future");
        }
        if (request.getMaxRetries() != null && request.getMaxRetries() < 0) {
            errors.put("maxRetries", "Max retries must be non-negative");
        }
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() <= 0) {
            errors.put("timeoutSeconds", "Timeout must be positive");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Invalid job configuration", errors);
        }
    }
}
