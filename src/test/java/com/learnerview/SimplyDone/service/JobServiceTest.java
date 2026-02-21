package com.learnerview.SimplyDone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobSubmissionResponse;
import com.learnerview.SimplyDone.model.*;
import com.learnerview.SimplyDone.repository.JobRepository;
import com.learnerview.SimplyDone.service.impl.JobServiceImpl;
import com.learnerview.SimplyDone.service.strategy.JobExecutionStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService Tests")
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private RetryService retryService;

    @Mock
    private JobExecutor jobExecutor;

    @Mock
    private JobExecutorFactory jobExecutorFactory;

    private JobServiceImpl jobService;

    @BeforeEach
    void setUp() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer timer = Timer.builder("job.test.timer").register(registry);
        jobService = new JobServiceImpl(jobRepository, rateLimitingService, retryService, jobExecutor, timer, jobExecutorFactory, new ObjectMapper());
    }

    private JobSubmissionRequest buildRequest(JobPriority priority, int delaySeconds) {
        JobSubmissionRequest req = new JobSubmissionRequest();
        req.setMessage("Test job");
        req.setPriority(priority);
        req.setDelaySeconds(delaySeconds);
        req.setUserId("user-1");
        req.setJobType(JobType.API_CALL);
        return req;
    }

    // -------------------------------------------------------
    // submitJob
    // -------------------------------------------------------

    @Test
    @DisplayName("submitJob returns response with non-null job ID")
    void submitJob_returnsJobId() {
        JobSubmissionResponse response = jobService.submitJob(buildRequest(JobPriority.HIGH, 0));

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotBlank();
    }

    @Test
    @DisplayName("submitJob saves the job to the repository")
    void submitJob_savesJobToRepository() {
        jobService.submitJob(buildRequest(JobPriority.HIGH, 0));

        verify(jobRepository, times(1)).saveJob(any(Job.class));
    }

    @Test
    @DisplayName("submitJob with delay schedules job in the future")
    void submitJob_withDelay_schedulesInFuture() {
        Instant before = Instant.now();
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);

        jobService.submitJob(buildRequest(JobPriority.LOW, 30));

        verify(jobRepository).saveJob(captor.capture());
        assertThat(captor.getValue().getExecuteAt()).isAfter(before.plusSeconds(29));
    }

    @Test
    @DisplayName("submitJob defaults to LOW priority when null")
    void submitJob_nullPriority_defaultsToLow() {
        JobSubmissionRequest req = buildRequest(null, 0);
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);

        jobService.submitJob(req);

        verify(jobRepository).saveJob(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(JobPriority.LOW);
    }

    // -------------------------------------------------------
    // executeNextReadyJob
    // -------------------------------------------------------

    @Test
    @DisplayName("executeNextReadyJob returns false when queues are empty")
    void executeNextReadyJob_emptyQueues_returnsFalse() {
        when(jobRepository.getNextReadyJob(JobPriority.HIGH)).thenReturn(null);
        when(jobRepository.getNextReadyJob(JobPriority.LOW)).thenReturn(null);

        assertThat(jobService.executeNextReadyJob()).isFalse();
    }

    @Test
    @DisplayName("executeNextReadyJob picks HIGH priority over LOW")
    void executeNextReadyJob_highPriorityFirst() throws Exception {
        Job highJob = Job.builder().id("high-1").jobType(JobType.API_CALL)
                .userId("u").priority(JobPriority.HIGH).message("hi").executeAt(Instant.now()).build();

        when(jobRepository.getNextReadyJob(JobPriority.HIGH)).thenReturn(highJob);

        jobService.executeNextReadyJob();

        verify(jobExecutor, times(1)).execute(highJob);
        verify(jobRepository, never()).getNextReadyJob(JobPriority.LOW);
    }

    @Test
    @DisplayName("executeNextReadyJob falls back to LOW when HIGH queue is empty")
    void executeNextReadyJob_fallsBackToLow() throws Exception {
        Job lowJob = Job.builder().id("low-1").jobType(JobType.API_CALL)
                .userId("u").priority(JobPriority.LOW).message("lo").executeAt(Instant.now()).build();

        when(jobRepository.getNextReadyJob(JobPriority.HIGH)).thenReturn(null);
        when(jobRepository.getNextReadyJob(JobPriority.LOW)).thenReturn(lowJob);

        assertThat(jobService.executeNextReadyJob()).isTrue();
        verify(jobExecutor).execute(lowJob);
    }

    @Test
    @DisplayName("executeNextReadyJob increments the executed counter on success")
    void executeNextReadyJob_success_incrementsExecutedCounter() throws Exception {
        Job job = Job.builder().id("j-1").jobType(JobType.API_CALL)
                .userId("u").priority(JobPriority.HIGH).message("m").executeAt(Instant.now()).build();
        when(jobRepository.getNextReadyJob(JobPriority.HIGH)).thenReturn(job);
        doNothing().when(jobExecutor).execute(any());

        jobService.executeNextReadyJob();

        verify(jobRepository).incrementExecutedJobsCounter();
    }

    @Test
    @DisplayName("executeNextReadyJob calls retryJob when execution throws")
    void executeNextReadyJob_executionFails_triggersRetry() throws Exception {
        Job job = Job.builder().id("j-1").jobType(JobType.API_CALL)
                .userId("u").priority(JobPriority.HIGH).message("m").executeAt(Instant.now()).build();
        when(jobRepository.getNextReadyJob(JobPriority.HIGH)).thenReturn(job);
        RuntimeException error = new RuntimeException("execution failed");
        doThrow(error).when(jobExecutor).execute(any());

        boolean result = jobService.executeNextReadyJob();

        assertThat(result).isFalse();
        verify(retryService).retryJob(eq(job), any(Exception.class));
        verify(jobRepository, never()).incrementExecutedJobsCounter();
    }

    // -------------------------------------------------------
    // cancelJob / getJobById / getQueueSizes
    // -------------------------------------------------------

    @Test
    @DisplayName("cancelJob delegates to repository")
    void cancelJob_delegatesToRepository() {
        when(jobRepository.deleteJob("job-42")).thenReturn(true);

        assertThat(jobService.cancelJob("job-42")).isTrue();
    }

    @Test
    @DisplayName("cancelJob returns false for unknown job ID")
    void cancelJob_unknownId_returnsFalse() {
        when(jobRepository.deleteJob("unknown")).thenReturn(false);

        assertThat(jobService.cancelJob("unknown")).isFalse();
    }

    @Test
    @DisplayName("getJobById delegates to repository")
    void getJobById_delegatesToRepository() {
        Job job = Job.builder().id("j").jobType(JobType.API_CALL).userId("u")
                .priority(JobPriority.HIGH).message("m").executeAt(Instant.now()).build();
        when(jobRepository.getJobById("j")).thenReturn(job);

        assertThat(jobService.getJobById("j")).isEqualTo(job);
    }

    @Test
    @DisplayName("getJobById returns null for unknown ID")
    void getJobById_unknownId_returnsNull() {
        when(jobRepository.getJobById("bad")).thenReturn(null);

        assertThat(jobService.getJobById("bad")).isNull();
    }

    @Test
    @DisplayName("getQueueSizes returns array of two non-negative longs")
    void getQueueSizes_returnsTwoValues() {
        when(jobRepository.getQueueSize(JobPriority.HIGH)).thenReturn(4L);
        when(jobRepository.getQueueSize(JobPriority.LOW)).thenReturn(2L);

        long[] sizes = jobService.getQueueSizes();

        assertThat(sizes).hasSize(2);
        assertThat(sizes[0]).isEqualTo(4L);
        assertThat(sizes[1]).isEqualTo(2L);
    }
}
