package com.learnerview.simplydone.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.dto.JobSubmissionRequest;
import com.learnerview.simplydone.dto.JobSubmissionResponse;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.mapper.JobMapper;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import com.learnerview.simplydone.service.RateLimiterService;
import com.learnerview.simplydone.service.SseEmitterService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobSubmissionServiceImplTest {

    @Test
    void submitReturnsExistingJobForSameProducerAndIdempotencyKey() {
        JobEntityRepository jobRepo = mock(JobEntityRepository.class);
        QueueRepository queueRepo = mock(QueueRepository.class);
        RateLimiterService rateLimiter = mock(RateLimiterService.class);
        SseEmitterService sse = mock(SseEmitterService.class);

        SchedulerProperties props = new SchedulerProperties();
        JobMapper mapper = new JobMapper(new ObjectMapper());

        JobSubmissionServiceImpl service = new JobSubmissionServiceImpl(
                jobRepo, queueRepo, rateLimiter, props, mapper, sse
        );

        JobSubmissionRequest.ExecutionRequest execution = new JobSubmissionRequest.ExecutionRequest();
        execution.setType("HTTP");
        execution.setEndpoint("https://api.example.com/process");

        JobSubmissionRequest req = new JobSubmissionRequest();
        req.setJobType("external");
        req.setProducer("order-service");
        req.setIdempotencyKey("order-123");
        req.setExecution(execution);

        JobEntity existing = JobEntity.builder()
                .id("job-existing")
                .jobType("external")
                .producer("order-service")
                .idempotencyKey("order-123")
                .priority(JobPriority.NORMAL)
                .status(JobStatus.QUEUED)
                .nextRunAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();

        when(jobRepo.findByProducerAndIdempotencyKey("order-service", "order-123"))
                .thenReturn(Optional.of(existing));

        JobSubmissionResponse response = service.submit(req);

        assertEquals("job-existing", response.getJobId());
        assertEquals("QUEUED", response.getStatus());
        verify(jobRepo, never()).save(any(JobEntity.class));
        verify(queueRepo, never()).enqueue(anyString(), any(), anyLong());
    }
}
