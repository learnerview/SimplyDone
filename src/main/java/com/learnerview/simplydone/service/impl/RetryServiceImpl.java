package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.entity.JobExecutionLog;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.JobExecutionLogRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import com.learnerview.simplydone.service.RetryService;
import com.learnerview.simplydone.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Retry with exponential backoff: delay = initialDelay * multiplier^attempt
 * Retry sequence (defaults): 5s → 10s → 20s → DLQ
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RetryServiceImpl implements RetryService {

    private final JobEntityRepository jobRepo;
    private final JobExecutionLogRepository logRepo;
    private final QueueRepository queueRepo;
    private final SchedulerProperties props;
    private final SseEmitterService sseEmitterService;

    @Override
    public void handleFailure(JobEntity job, String errorMessage, long durationMs) {
        int attempt = job.getAttemptCount();
        int maxRetries = job.getMaxRetries() > 0 ? job.getMaxRetries() : props.getRetry().getMaxAttempts();

        logRepo.save(JobExecutionLog.builder()
                .jobId(job.getId())
                .attempt(attempt)
                .status("FAILED")
                .message(errorMessage)
                .durationMs(durationMs)
                .build());

        if (attempt < maxRetries) {
            long delayMs = (long) (props.getRetry().getInitialDelaySeconds() * 1000
                    * Math.pow(props.getRetry().getBackoffMultiplier(), attempt));

            Instant nextRun = Instant.now().plusMillis(delayMs);
            job.setScheduledAt(nextRun);
            job.setStatus(JobStatus.QUEUED);
            job.setAttemptCount(attempt + 1);
            jobRepo.save(job);
            queueRepo.enqueue(job.getId(), job.getPriority(), nextRun.toEpochMilli());

            log.info("Retrying job {} (attempt {}/{}) in {}ms", job.getId(), attempt + 1, maxRetries, delayMs);

            sseEmitterService.broadcast("JOB_RETRY", Map.of(
                    "id", job.getId(), "jobType", job.getJobType(), "status", "QUEUED",
                    "attempt", attempt + 1, "maxRetries", maxRetries, "retryInMs", delayMs
            ));
        } else {
            job.setStatus(JobStatus.DLQ);
            job.setCompletedAt(Instant.now());
            job.setResult("Max retries exceeded: " + errorMessage);
            jobRepo.save(job);

            log.warn("Job {} moved to DLQ after {} attempts", job.getId(), attempt);

            sseEmitterService.broadcast("JOB_FAILED", Map.of(
                    "id", job.getId(), "jobType", job.getJobType(), "status", "DLQ",
                    "result", "Max retries exceeded: " + (errorMessage != null ? errorMessage : ""),
                    "attempts", attempt
            ));
        }
    }

    @Override
    public void logSuccess(JobEntity job, String message, long durationMs) {
        logRepo.save(JobExecutionLog.builder()
                .jobId(job.getId())
                .attempt(job.getAttemptCount())
                .status("SUCCESS")
                .message(message)
                .durationMs(durationMs)
                .build());
    }
}
