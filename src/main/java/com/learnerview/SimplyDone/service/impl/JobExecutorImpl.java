package com.learnerview.SimplyDone.service.impl;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.service.JobExecutor;
import com.learnerview.SimplyDone.service.JobExecutorFactory;
import com.learnerview.SimplyDone.service.strategy.JobExecutionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Default implementation of JobExecutor that delegates to job type-specific strategies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutorImpl implements JobExecutor {

    private final JobExecutorFactory jobExecutorFactory;

    @Override
    public void execute(Job job) throws Exception {
        if (job == null || job.getJobType() == null) {
            throw new IllegalArgumentException("Job and JobType cannot be null");
        }

        JobExecutionStrategy strategy = jobExecutorFactory.getStrategy(job.getJobType());

        if (strategy == null) {
            throw new IllegalStateException("No execution strategy found for job type: " + job.getJobType());
        }

        log.debug("Executing job {} with strategy {}", job.getId(), strategy.getClass().getSimpleName());
        strategy.execute(job);
    }

    @Override
    @Async
    public void executeAsync(Job job) {
        try {
            execute(job);
        } catch (Exception e) {
            log.error("Async execution failed for job {}: {}", job.getId(), e.getMessage(), e);
        }
    }

    @Override
    public boolean canExecute(Job job) {
        if (job == null || job.getJobType() == null) {
            return false;
        }

        return jobExecutorFactory.hasStrategy(job.getJobType());
    }

    @Override
    public long estimateExecutionTime(Job job) {
        if (!canExecute(job)) {
            return -1;
        }

        try {
            JobExecutionStrategy strategy = jobExecutorFactory.getStrategy(job.getJobType());
            JobExecutionStrategy.ResourceRequirements requirements = strategy.getResourceRequirements();

            // Simple estimation based on CPU units and memory requirements
            // More CPU/memory typically means longer running jobs
            // This is a heuristic - actual time depends on job implementation
            long baseTime = 5; // Base 5 seconds
            long cpuFactor = requirements.getCpuUnits() * 2;
            long memoryFactor = requirements.getMemoryMB() / 100;

            return baseTime + cpuFactor + memoryFactor;
        } catch (Exception e) {
            log.warn("Failed to estimate execution time for job {}: {}", job.getId(), e.getMessage());
            return -1;
        }
    }

    @Override
    public JobExecutionStrategy.ResourceRequirements getResourceRequirements(Job job) {
        if (!canExecute(job)) {
            throw new IllegalArgumentException("Cannot get resource requirements for job type: " + job.getJobType());
        }

        JobExecutionStrategy strategy = jobExecutorFactory.getStrategy(job.getJobType());
        return strategy.getResourceRequirements();
    }
}
