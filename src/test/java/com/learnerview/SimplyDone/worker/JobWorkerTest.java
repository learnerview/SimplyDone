package com.learnerview.SimplyDone.worker;

import com.learnerview.SimplyDone.config.SchedulerProperties;
import com.learnerview.SimplyDone.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobWorker Tests")
class JobWorkerTest {

    @Mock
    private JobService jobService;

    private SchedulerProperties schedulerProperties;
    private JobWorker jobWorker;

    @BeforeEach
    void setUp() {
        schedulerProperties = new SchedulerProperties();
        schedulerProperties.getWorker().setIntervalMs(1000);
        jobWorker = new JobWorker(jobService, schedulerProperties);
    }

    @Test
    @DisplayName("processJobs calls executeNextReadyJob on each cycle")
    void processJobs_callsExecuteNextReadyJob() {
        when(jobService.executeNextReadyJob()).thenReturn(false);

        jobWorker.processJobs();

        verify(jobService, times(1)).executeNextReadyJob();
    }

    @Test
    @DisplayName("processJobs does not throw when a job executes successfully")
    void processJobs_successfulExecution_doesNotThrow() {
        when(jobService.executeNextReadyJob()).thenReturn(true);

        assertThatNoException().isThrownBy(() -> jobWorker.processJobs());
    }

    @Test
    @DisplayName("processJobs does not propagate DataAccessException — logs and continues")
    void processJobs_redisConnectionError_doesNotPropagate() {
        when(jobService.executeNextReadyJob())
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertThatNoException().isThrownBy(() -> jobWorker.processJobs());
    }

    @Test
    @DisplayName("processJobs does not propagate unexpected RuntimeException")
    void processJobs_unexpectedException_doesNotPropagate() {
        when(jobService.executeNextReadyJob()).thenThrow(new RuntimeException("unexpected"));

        assertThatNoException().isThrownBy(() -> jobWorker.processJobs());
    }

    @Test
    @DisplayName("getStats returns non-null stats after first cycle")
    void getStats_returnsStatsAfterCycle() {
        when(jobService.executeNextReadyJob()).thenReturn(false);

        jobWorker.processJobs();
        JobWorker.WorkerStats stats = jobWorker.getStats();

        assertThat(stats).isNotNull();
        assertThat(stats.totalCycles()).isEqualTo(1);
        assertThat(stats.jobsExecuted()).isEqualTo(0);
    }

    @Test
    @DisplayName("getStats increments jobsExecuted counter on each successful job")
    void getStats_jobsExecutedIncrements() {
        when(jobService.executeNextReadyJob()).thenReturn(true);

        jobWorker.processJobs();
        jobWorker.processJobs();

        JobWorker.WorkerStats stats = jobWorker.getStats();
        assertThat(stats.totalCycles()).isEqualTo(2);
        assertThat(stats.jobsExecuted()).isEqualTo(2);
    }

    @Test
    @DisplayName("getStats tracks consecutive Redis failure counter")
    void getStats_redisFailures_incrementsCounter() {
        when(jobService.executeNextReadyJob())
                .thenThrow(new DataAccessResourceFailureException("Redis down"))
                .thenThrow(new DataAccessResourceFailureException("Redis down"))
                .thenReturn(false);

        jobWorker.processJobs();
        jobWorker.processJobs();
        jobWorker.processJobs();

        JobWorker.WorkerStats stats = jobWorker.getStats();
        assertThat(stats.redisFailures()).isEqualTo(2);
    }

    @Test
    @DisplayName("getStats resets Redis failure counter after a successful cycle")
    void getStats_redisFailures_resetAfterSuccess() {
        when(jobService.executeNextReadyJob())
                .thenThrow(new DataAccessResourceFailureException("Redis down"))
                .thenReturn(true);

        jobWorker.processJobs(); // failure
        jobWorker.processJobs(); // success — should reset counter

        JobWorker.WorkerStats stats = jobWorker.getStats();
        assertThat(stats.redisFailures()).isEqualTo(0);
    }

    @Test
    @DisplayName("intervalMs comes from schedulerProperties")
    void getStats_intervalMsMatchesConfiguration() {
        schedulerProperties.getWorker().setIntervalMs(5000);
        jobWorker = new JobWorker(jobService, schedulerProperties);

        assertThat(jobWorker.getStats().intervalMs()).isEqualTo(5000);
    }
}
