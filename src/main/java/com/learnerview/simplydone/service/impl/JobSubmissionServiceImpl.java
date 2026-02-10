package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.dto.JobResponse;
import com.learnerview.simplydone.dto.JobSubmissionRequest;
import com.learnerview.simplydone.dto.JobSubmissionResponse;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.exception.JobNotFoundException;
import com.learnerview.simplydone.handler.JobHandlerRegistry;
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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobSubmissionServiceImpl implements JobSubmissionService {

    private final JobEntityRepository jobRepo;
    private final QueueRepository queueRepo;
    private final JobHandlerRegistry registry;
    private final RateLimiterService rateLimiter;
    private final SchedulerProperties props;
    private final JobMapper jobMapper;
    private final SseEmitterService sseEmitterService;

    @Override
    public JobSubmissionResponse submit(JobSubmissionRequest req) {
        registry.getHandler(req.getJobType());
        rateLimiter.checkRateLimit(req.getUserId());

        JobPriority priority = jobMapper.parsePriority(req.getPriority());
        Instant scheduledAt = req.getScheduledAt() != null ? req.getScheduledAt() : Instant.now();
        String jobId = UUID.randomUUID().toString();

        JobEntity job = JobEntity.builder()
                .id(jobId)
                .jobType(req.getJobType())
                .status(JobStatus.QUEUED)
                .priority(priority)
                .payload(jobMapper.serializePayload(req.getPayload()))
                .userId(req.getUserId())
                .scheduledAt(scheduledAt)
                .maxRetries(req.getMaxRetries() != null ? req.getMaxRetries() : props.getRetry().getMaxAttempts())
                .build();

        jobRepo.save(job);
        queueRepo.enqueue(jobId, priority, scheduledAt.toEpochMilli());
        log.info("Job submitted: {} type={} priority={}", jobId, req.getJobType(), priority);

        sseEmitterService.broadcast("JOB_CREATED", Map.of(
                "id", jobId,
                "jobType", req.getJobType(),
                "status", "QUEUED",
                "priority", priority.name(),
                "userId", req.getUserId() != null ? req.getUserId() : "anonymous"
        ));

        return JobSubmissionResponse.builder()
                .jobId(jobId)
                .status(JobStatus.QUEUED.name())
                .jobType(req.getJobType())
                .priority(priority.name())
                .scheduledAt(scheduledAt)
                .build();
    }

    @Override
    public JobResponse getJob(String jobId) {
        JobEntity job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return jobMapper.toResponse(job);
    }

    @Override
    public void cancelJob(String jobId) {
        JobEntity job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        if (job.getStatus() == JobStatus.QUEUED) {
            queueRepo.remove(jobId, job.getPriority());
            job.setStatus(JobStatus.FAILED);
            job.setResult("Cancelled by user");
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
            sseEmitterService.broadcast("JOB_UPDATE", Map.of(
                    "id", jobId, "status", "FAILED", "result", "Cancelled by user"
            ));
        } else {
            throw new IllegalArgumentException("Can only cancel QUEUED jobs, current: " + job.getStatus());
        }
    }
}
