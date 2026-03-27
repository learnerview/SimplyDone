package com.learnerview.simplydone.service;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorkerMaintenanceServiceTest {

    @Test
    void promoteRetriesMovesDueJobsToQueuedAndEnqueues() {
        JobEntityRepository jobRepo = mock(JobEntityRepository.class);
        QueueRepository queueRepo = mock(QueueRepository.class);
        RetryService retryService = mock(RetryService.class);
        SchedulerProperties props = new SchedulerProperties();

        WorkerMaintenanceService service = new WorkerMaintenanceService(jobRepo, queueRepo, retryService, props);

        JobEntity due = JobEntity.builder()
                .id("job-1")
                .status(JobStatus.RETRY_SCHEDULED)
                .priority(JobPriority.NORMAL)
                .nextRunAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepo.findTop100ByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(eq(JobStatus.RETRY_SCHEDULED), any()))
                .thenReturn(List.of(due));

        service.promoteRetries();

        verify(jobRepo).save(argThat(j -> j.getStatus() == JobStatus.QUEUED));
        verify(queueRepo).enqueue(eq("job-1"), eq(JobPriority.NORMAL), anyLong());
    }

    @Test
    void recoverExpiredLeasesDelegatesToRetryHandler() {
        JobEntityRepository jobRepo = mock(JobEntityRepository.class);
        QueueRepository queueRepo = mock(QueueRepository.class);
        RetryService retryService = mock(RetryService.class);
        SchedulerProperties props = new SchedulerProperties();

        WorkerMaintenanceService service = new WorkerMaintenanceService(jobRepo, queueRepo, retryService, props);

        JobEntity expired = JobEntity.builder()
                .id("job-expired")
                .status(JobStatus.RUNNING)
                .visibleAt(Instant.now().minusSeconds(10))
                .build();

        when(jobRepo.findTop100ByStatusAndVisibleAtBeforeOrderByVisibleAtAsc(eq(JobStatus.RUNNING), any()))
                .thenReturn(List.of(expired));

        service.recoverExpiredLeases();

        verify(retryService).handleFailure(eq(expired), eq("Worker lease expired"), eq(0L));
    }
}
