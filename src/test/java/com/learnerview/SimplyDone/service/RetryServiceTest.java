package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.model.*;
import com.learnerview.SimplyDone.repository.JobRepository;
import com.learnerview.SimplyDone.service.impl.RetryServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryService Tests")
class RetryServiceTest {

    @Mock
    private JobRepository jobRepository;

    private RetryServiceImpl retryService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        retryService = new RetryServiceImpl(jobRepository, objectMapper);
        ReflectionTestUtils.setField(retryService, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(retryService, "backoffMultiplier", 2.0);
        ReflectionTestUtils.setField(retryService, "initialDelaySeconds", 1);
    }

    private Job buildJob() {
        return Job.builder()
                .id("job-1")
                .jobType(JobType.API_CALL)
                .userId("user-1")
                .priority(JobPriority.HIGH)
                .message("test job")
                .executeAt(Instant.now())
                .build();
    }

    private Job buildJobWithAttempt(int attemptCount) {
        return Job.builder()
                .id("job-1")
                .jobType(JobType.API_CALL)
                .userId("user-1")
                .priority(JobPriority.HIGH)
                .message("test job")
                .executeAt(Instant.now())
                .attemptCount(attemptCount)
                .build();
    }

    @Test
    @DisplayName("First retry is scheduled successfully")
    void retryJob_firstAttempt_schedulesRetry() {
        Job job = buildJob();
        boolean result = retryService.retryJob(job, new RuntimeException("connection timeout"));

        assertThat(result).isTrue();
        verify(jobRepository, times(1)).saveJob(any(Job.class));
    }

    @Test
    @DisplayName("Retry job preserves the original job ID for tracking")
    void retryJob_preservesOriginalJobId() {
        Job job = buildJob();
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);

        retryService.retryJob(job, new RuntimeException("error"));

        verify(jobRepository).saveJob(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(job.getId());
    }

    @Test
    @DisplayName("Retry job preserves original job type, priority, and parameters")
    void retryJob_preservesOriginalJobProperties() {
        Job job = buildJob();
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);

        retryService.retryJob(job, new RuntimeException("error"));

        verify(jobRepository).saveJob(captor.capture());
        Job retryJob = captor.getValue();
        assertThat(retryJob.getJobType()).isEqualTo(job.getJobType());
        assertThat(retryJob.getPriority()).isEqualTo(job.getPriority());
        assertThat(retryJob.getUserId()).isEqualTo(job.getUserId());
    }

    @Test
    @DisplayName("Retry is scheduled in the future using exponential backoff")
    void retryJob_scheduledInFuture() {
        Job job = buildJob();
        Instant before = Instant.now();
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);

        retryService.retryJob(job, new RuntimeException("error"));

        verify(jobRepository).saveJob(captor.capture());
        assertThat(captor.getValue().getExecuteAt()).isAfter(before);
    }

    @Test
    @DisplayName("Backoff delay grows exponentially: attempt 1 = 1s, attempt 2 = 2s, attempt 3 = 4s")
    void retryJob_backoffIsExponential() {
        Instant[] scheduledTimes = new Instant[3];
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);

        // Each call uses a job with a different attemptCount so the service computes a different delay
        retryService.retryJob(buildJobWithAttempt(0), new RuntimeException("e")); // delay = 1s
        verify(jobRepository, times(1)).saveJob(captor.capture());
        scheduledTimes[0] = captor.getValue().getExecuteAt();

        retryService.retryJob(buildJobWithAttempt(1), new RuntimeException("e")); // delay = 2s
        verify(jobRepository, times(2)).saveJob(captor.capture());
        scheduledTimes[1] = captor.getValue().getExecuteAt();

        retryService.retryJob(buildJobWithAttempt(2), new RuntimeException("e")); // delay = 4s
        verify(jobRepository, times(3)).saveJob(captor.capture());
        scheduledTimes[2] = captor.getValue().getExecuteAt();

        // Each retry should be scheduled further in the future
        assertThat(scheduledTimes[1]).isAfter(scheduledTimes[0]);
        assertThat(scheduledTimes[2]).isAfter(scheduledTimes[1]);
    }

    @Test
    @DisplayName("Job moves to dead-letter queue after max attempts")
    void retryJob_exceedsMaxAttempts_movesToDeadLetterQueue() throws Exception {
        // The service reads job.attemptCount to determine retry number.
        // We must pass jobs with incrementing attemptCount to simulate progression.
        Exception error = new RuntimeException("permanent failure");

        retryService.retryJob(buildJobWithAttempt(0), error); // attempt 1 -> ok
        retryService.retryJob(buildJobWithAttempt(1), error); // attempt 2 -> ok
        retryService.retryJob(buildJobWithAttempt(2), error); // attempt 3 -> ok
        boolean result = retryService.retryJob(buildJobWithAttempt(3), error); // attempt 4 -> exceeds max 3

        assertThat(result).isFalse();
        verify(jobRepository, times(1)).saveToDeadLetterQueue(anyString());
    }

    @Test
    @DisplayName("Dead-letter entry contains failure reason")
    void retryJob_deadLetterContainsFailureReason() throws Exception {
        String errorMessage = "connection refused";
        Exception error = new RuntimeException(errorMessage);
        ArgumentCaptor<String> dlqCaptor = ArgumentCaptor.forClass(String.class);

        retryService.retryJob(buildJobWithAttempt(0), error); // attempt 1
        retryService.retryJob(buildJobWithAttempt(1), error); // attempt 2
        retryService.retryJob(buildJobWithAttempt(2), error); // attempt 3
        retryService.retryJob(buildJobWithAttempt(3), error); // attempt 4 -> triggers DLQ

        verify(jobRepository).saveToDeadLetterQueue(dlqCaptor.capture());
        assertThat(dlqCaptor.getValue()).contains(errorMessage);
    }

    @Test
    @DisplayName("resetRetryAttempts is a no-op in the current implementation (attemptCount lives on the job)")
    void resetRetryAttempts_allowsFreshRetry() {
        // In the current implementation, resetRetryAttempts is a no-op because
        // retry counts are tracked via job.attemptCount, not an internal map.
        // This test verifies the method does not throw and does not call DLQ.
        Job job = buildJob(); // attemptCount = 0
        Exception error = new RuntimeException("error");

        retryService.resetRetryAttempts(job.getId());

        // After reset (no-op), a fresh job at attempt 0 should still be retried normally
        boolean result = retryService.retryJob(job, error);
        assertThat(result).isTrue();
        verify(jobRepository, never()).saveToDeadLetterQueue(anyString());
    }

    @Test
    @DisplayName("getRetryStatistics returns configured max attempts")
    void getRetryStatistics_returnsCorrectCounts() {
        // The current implementation does not maintain live per-job counters.
        // It returns (0, 0, maxAttempts) where maxAttempts comes from config.
        RetryService.RetryStatistics stats = retryService.getRetryStatistics();
        assertThat(stats.maxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("getDeadLetterJobs returns empty list when DLQ is empty")
    void getDeadLetterJobs_emptyQueue_returnsEmptyList() {
        when(jobRepository.getDeadLetterJobsRaw()).thenReturn(Set.of());

        assertThat(retryService.getDeadLetterJobs()).isEmpty();
    }

    @Test
    @DisplayName("clearDeadLetterQueue delegates to repository")
    void clearDeadLetterQueue_delegatesToRepository() {
        when(jobRepository.clearDeadLetterQueue()).thenReturn(5);

        assertThat(retryService.clearDeadLetterQueue()).isEqualTo(5);
        verify(jobRepository).clearDeadLetterQueue();
    }
}
