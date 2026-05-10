package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.dto.JobResponse;
import com.learnerview.simplydone.dto.JobSubmissionRequest;
import com.learnerview.simplydone.dto.JobSubmissionResponse;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.exception.JobNotFoundException;
import com.learnerview.simplydone.exception.QueueFullException;
import com.learnerview.simplydone.mapper.JobMapper;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import com.learnerview.simplydone.service.JobSubmissionService;
import com.learnerview.simplydone.service.RateLimiterService;
import com.learnerview.simplydone.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;

@Service
@Profile("api")
@Slf4j
@RequiredArgsConstructor
public class JobSubmissionServiceImpl implements JobSubmissionService {

    private final JobEntityRepository jobRepo;
    private final QueueRepository queueRepo;
    private final RateLimiterService rateLimiter;
    private final SchedulerProperties props;
    private final JobMapper jobMapper;
    private final SseEmitterService sseEmitterService;

    @Override
    public JobSubmissionResponse submit(String producer, JobSubmissionRequest req) {
        rateLimiter.checkRateLimit(producer);

        if (!"HTTP".equalsIgnoreCase(req.getExecution().getType())) {
            throw new IllegalArgumentException("Unsupported execution.type: " + req.getExecution().getType());
        }

        validateHttpUrl(req.getExecution().getEndpoint(), "execution.endpoint");
        if (req.getCallbackUrl() != null && !req.getCallbackUrl().isBlank()) {
            validateHttpUrl(req.getCallbackUrl(), "callbackUrl");
        }

        long totalDepth = getTotalQueueDepthWithFallback();
        if (totalDepth >= props.getQueue().getMaxDepth()) {
            throw new QueueFullException(props.getQueue().getMaxDepth());
        }

        JobEntity existing = jobRepo.findByProducerAndIdempotencyKey(producer, req.getIdempotencyKey())
            .orElse(null);
        if (existing != null) {
            return JobSubmissionResponse.builder()
                .jobId(existing.getId())
                .status(existing.getStatus().name())
                .jobType(existing.getJobType())
                .priority(existing.getPriority().name())
                .scheduledAt(existing.getNextRunAt())
                .build();
        }

        JobPriority priority = jobMapper.parsePriority(req.getPriority());
        Instant nextRunAt = req.getNextRunAt() != null ? req.getNextRunAt() : Instant.now();
        String jobId = UUID.randomUUID().toString();

        JobEntity job = JobEntity.builder()
                .id(jobId)
                .jobType(req.getJobType())
            .producer(producer)
            .idempotencyKey(req.getIdempotencyKey())
                .status(JobStatus.QUEUED)
                .priority(priority)
                .payload(jobMapper.serializePayload(req.getPayload()))
            .executionType(req.getExecution().getType())
            .executionEndpoint(req.getExecution().getEndpoint())
            .timeoutSeconds(req.getTimeoutSeconds())
            .callbackUrl(req.getCallbackUrl())
            .nextRunAt(nextRunAt)
            .maxAttempts(req.getMaxAttempts() != null ? req.getMaxAttempts() : props.getRetry().getMaxAttempts())
                .build();

        jobRepo.save(job);
        try {
            queueRepo.enqueue(jobId, priority, nextRunAt.toEpochMilli());
        } catch (RuntimeException e) {
            log.warn("Redis queue unavailable for job {}, keeping DB fallback only: {}", jobId, e.getMessage());
        }
        log.info("Job submitted: {} type={} priority={}", jobId, req.getJobType(), priority);

        sseEmitterService.broadcast(producer, "JOB_CREATED", Map.of(
                "id", jobId,
                "jobType", req.getJobType(),
                "status", "QUEUED",
                "priority", priority.name(),
            "producer", producer
        ));

        return JobSubmissionResponse.builder()
                .jobId(jobId)
                .status(JobStatus.QUEUED.name())
                .jobType(req.getJobType())
                .priority(priority.name())
            .scheduledAt(nextRunAt)
                .build();
    }

    @Override
    public JobResponse getJob(String producer, String jobId) {
        JobEntity job = jobRepo.findByProducerAndId(producer, jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return jobMapper.toResponse(job);
    }

    @Override
    public JobResponse getJob(String jobId) {
        JobEntity job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return jobMapper.toResponse(job);
    }

    @Override
    public void cancelJob(String producer, String jobId) {
        JobEntity job = jobRepo.findByProducerAndId(producer, jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        if (job.getStatus() == JobStatus.QUEUED) {
            try {
                queueRepo.remove(jobId, job.getPriority());
            } catch (RuntimeException e) {
                log.warn("Redis queue unavailable while cancelling job {}: {}", jobId, e.getMessage());
            }
            job.setStatus(JobStatus.CANCELLED);
            job.setVisibleAt(null);
            job.setLeaseOwner(null);
            job.setLeaseToken(null);
            job.setResult("Cancelled by user");
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
            sseEmitterService.broadcast(producer, "JOB_UPDATE", Map.of(
                    "id", jobId, "status", "CANCELLED", "result", "Cancelled by user"
            ));
        } else {
            throw new IllegalArgumentException("Can only cancel QUEUED jobs, current: " + job.getStatus());
        }
    }

    @Override
    public org.springframework.data.domain.Page<JobResponse> listJobs(String producer, org.springframework.data.domain.Pageable pageable) {
        return jobRepo.findByProducerOrderByCreatedAtDesc(producer, pageable).map(jobMapper::toResponse);
    }

    @Override
    public org.springframework.data.domain.Page<JobResponse> listJobs(org.springframework.data.domain.Pageable pageable) {
        return jobRepo.findAllByOrderByCreatedAtDesc(pageable).map(jobMapper::toResponse);
    }

    @Override
    public java.util.List<JobResponse> getDlqJobs(String producer) {
        return jobRepo.findByProducerAndStatus(producer, JobStatus.DLQ).stream()
                .map(jobMapper::toResponse).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void retryDlqJob(String producer, String jobId) {
        JobEntity job = jobRepo.findByProducerAndId(producer, jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        if (job.getStatus() != JobStatus.DLQ) {
            throw new IllegalArgumentException("Job is not in DLQ: " + job.getStatus());
        }
        job.setStatus(JobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setNextRunAt(Instant.now());
        job.setCompletedAt(null);
        job.setResult(null);
        jobRepo.save(job);
        try {
            queueRepo.enqueue(jobId, job.getPriority(), Instant.now().toEpochMilli());
        } catch (RuntimeException e) {
            log.warn("Redis queue unavailable while retrying DLQ job {}: {}", jobId, e.getMessage());
        }
        sseEmitterService.broadcast(producer, "JOB_UPDATE",
                Map.of("id", jobId, "status", "QUEUED", "result", "Retried from DLQ"));
    }

    private long getTotalQueueDepthWithFallback() {
        try {
            return queueRepo.queueSize(JobPriority.HIGH)
                    + queueRepo.queueSize(JobPriority.NORMAL)
                    + queueRepo.queueSize(JobPriority.LOW);
        } catch (RuntimeException e) {
            log.warn("Redis queue depth check failed, using DB fallback: {}", e.getMessage());
            return jobRepo.countByStatus(JobStatus.QUEUED);
        }
    }

    private void validateHttpUrl(String value, String fieldName) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException(fieldName + " must use http or https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException(fieldName + " must be a valid absolute URL");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid absolute URL");
        }
    }
}
