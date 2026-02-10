package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.handler.JobContext;
import com.learnerview.simplydone.handler.JobHandler;
import com.learnerview.simplydone.handler.JobHandlerRegistry;
import com.learnerview.simplydone.handler.JobResult;
import com.learnerview.simplydone.mapper.JobMapper;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.service.JobExecutorService;
import com.learnerview.simplydone.service.RetryService;
import com.learnerview.simplydone.service.SseEmitterService;
import com.learnerview.simplydone.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobExecutorServiceImpl implements JobExecutorService {

    private final JobHandlerRegistry registry;
    private final JobEntityRepository jobRepo;
    private final RetryService retryService;
    private final WorkflowService workflowService;
    private final JobMapper jobMapper;
    private final SseEmitterService sseEmitterService;

    @Override
    public void execute(JobEntity job) {
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobRepo.save(job);

        sseEmitterService.broadcast("JOB_STARTED", Map.of(
                "id", job.getId(), "jobType", job.getJobType(), "status", "RUNNING",
                "priority", job.getPriority().name()
        ));

        long start = System.currentTimeMillis();
        try {
            JobHandler handler = registry.getHandler(job.getJobType());
            Map<String, Object> payload = jobMapper.deserializePayload(job.getPayload());

            JobContext ctx = JobContext.builder()
                    .jobId(job.getId())
                    .jobType(job.getJobType())
                    .payload(payload)
                    .attemptNumber(job.getAttemptCount())
                    .scheduledAt(job.getScheduledAt())
                    .userId(job.getUserId())
                    .build();

            JobResult result = handler.execute(ctx);
            long durationMs = System.currentTimeMillis() - start;

            if (result.isSuccess()) {
                job.setStatus(JobStatus.SUCCESS);
                job.setResult(result.getMessage());
                job.setCompletedAt(Instant.now());
                jobRepo.save(job);
                retryService.logSuccess(job, result.getMessage(), durationMs);
                log.info("Job {} completed in {}ms", job.getId(), durationMs);

                sseEmitterService.broadcast("JOB_COMPLETED", Map.of(
                        "id", job.getId(), "jobType", job.getJobType(), "status", "SUCCESS",
                        "result", result.getMessage() != null ? result.getMessage() : "",
                        "durationMs", durationMs
                ));

                workflowService.unblockDependents(job);
            } else {
                retryService.handleFailure(job, result.getMessage(), durationMs);
            }
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            retryService.handleFailure(job,
                    e.getMessage() != null ? e.getMessage() : "Unknown error", durationMs);
        }
    }
}
