package com.learnerview.simplydone.service;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerEngineTest {

    @Mock
    private QueueRepository queueRepo;

    @Mock
    private JobEntityRepository jobRepo;

    @Mock
    private JobExecutorService executor;

    @Test
    void pollClaimsHighestPriorityReadyJob() {
        SchedulerProperties props = new SchedulerProperties();
        SchedulerEngine schedulerEngine = new SchedulerEngine(queueRepo, jobRepo, executor, props);

        JobEntity job = JobEntity.builder()
                .id("job-1")
                .producer("tenant-a")
                .jobType("webhook")
                .status(JobStatus.QUEUED)
                .priority(JobPriority.HIGH)
                .nextRunAt(Instant.now())
                .build();

        when(queueRepo.queueSize(JobPriority.HIGH)).thenReturn(1L);
        when(queueRepo.claimNextReady(JobPriority.HIGH)).thenReturn(Optional.of("job-1"));
        when(jobRepo.claimForExecution(anyString(), anyString(), anyString(), any(Instant.class), any(Instant.class),
                eq(JobStatus.QUEUED), eq(JobStatus.RUNNING))).thenReturn(1);
        when(jobRepo.findById("job-1")).thenReturn(Optional.of(job));

        schedulerEngine.poll();

        verify(queueRepo).claimNextReady(JobPriority.HIGH);
        verify(queueRepo, never()).claimNextReady(JobPriority.NORMAL);
        verify(queueRepo, never()).claimNextReady(JobPriority.LOW);
        verify(jobRepo).claimForExecution(eq("job-1"), anyString(), anyString(), any(Instant.class), any(Instant.class),
                eq(JobStatus.QUEUED), eq(JobStatus.RUNNING));
        verify(executor).execute(job);
    }

    @Test
    void pollStopsWhenNoReadyJobsExist() {
        SchedulerProperties props = new SchedulerProperties();
        SchedulerEngine schedulerEngine = new SchedulerEngine(queueRepo, jobRepo, executor, props);

        when(queueRepo.queueSize(JobPriority.HIGH)).thenReturn(0L);
        when(queueRepo.queueSize(JobPriority.NORMAL)).thenReturn(0L);
        when(queueRepo.queueSize(JobPriority.LOW)).thenReturn(0L);

        schedulerEngine.poll();

        verify(queueRepo, never()).claimNextReady(any(JobPriority.class));
        verifyNoInteractions(jobRepo, executor);
    }
}