package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.entity.JobExecutionLog;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.JobExecutionLogRepository;
import com.learnerview.simplydone.service.SseEmitterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryServiceImplTest {

    @Mock
    private JobEntityRepository jobRepo;

    @Mock
    private JobExecutionLogRepository logRepo;

    @Mock
    private SseEmitterService sseEmitterService;

    private RetryServiceImpl retryService;

    @BeforeEach
    void setUp() {
        SchedulerProperties props = new SchedulerProperties();
        props.getRetry().setInitialDelaySeconds(5);
        props.getRetry().setBackoffMultiplier(2.0);
        props.getRetry().setMaxAttempts(3);
        retryService = new RetryServiceImpl(jobRepo, logRepo, props, sseEmitterService);
    }

    @Test
    void handleFailureSchedulesRetryWhenAttemptsRemain() {
        JobEntity job = JobEntity.builder()
                .id("job-1")
                .producer("tenant-a")
                .jobType("webhook")
                .priority(JobPriority.HIGH)
                .status(JobStatus.RUNNING)
                .attemptCount(1)
                .maxAttempts(3)
                .build();

        when(jobRepo.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(logRepo.save(any(JobExecutionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        retryService.handleFailure(job, "boom", 123L);

        assertEquals(JobStatus.RETRY_SCHEDULED, job.getStatus());
        assertEquals(2, job.getAttemptCount());
        assertNotNull(job.getNextRunAt());
        assertNull(job.getVisibleAt());
        assertNull(job.getLeaseOwner());
        assertNull(job.getLeaseToken());

        ArgumentCaptor<JobExecutionLog> logCaptor = ArgumentCaptor.forClass(JobExecutionLog.class);
        verify(logRepo).save(logCaptor.capture());
        assertEquals("job-1", logCaptor.getValue().getJobId());
        assertEquals(1, logCaptor.getValue().getAttempt());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
        assertEquals("boom", logCaptor.getValue().getMessage());
        assertEquals(123L, logCaptor.getValue().getDurationMs());

        verify(jobRepo).save(job);
        verify(sseEmitterService).broadcast("tenant-a", "JOB_RETRY", Map.of(
                "id", "job-1",
                "jobType", "webhook",
                "status", "RETRY_SCHEDULED",
                "attempt", 2,
                "maxAttempts", 3,
                "retryInMs", 10000L
        ));
    }

    @Test
    void handleFailureMovesJobToDlqWhenAttemptsAreExhausted() {
        JobEntity job = JobEntity.builder()
                .id("job-2")
                .producer("tenant-b")
                .jobType("webhook")
                .priority(JobPriority.NORMAL)
                .status(JobStatus.RUNNING)
                .attemptCount(3)
                .maxAttempts(3)
                .build();

        when(jobRepo.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(logRepo.save(any(JobExecutionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        retryService.handleFailure(job, "downstream unavailable", 250L);

        assertEquals(JobStatus.DLQ, job.getStatus());
        assertEquals(3, job.getAttemptCount());
        assertNotNull(job.getCompletedAt());
        assertEquals("Max retries exceeded: downstream unavailable", job.getResult());
        assertNull(job.getVisibleAt());
        assertNull(job.getLeaseOwner());
        assertNull(job.getLeaseToken());

        verify(jobRepo).save(job);
        verify(sseEmitterService).broadcast("tenant-b", "JOB_FAILED", Map.of(
                "id", "job-2",
                "jobType", "webhook",
                "status", "DLQ",
                "result", "Max retries exceeded: downstream unavailable",
                "attempts", 3
        ));
    }
}