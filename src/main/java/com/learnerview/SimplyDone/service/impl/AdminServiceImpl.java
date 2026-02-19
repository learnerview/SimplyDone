package com.learnerview.SimplyDone.service.impl;

import com.learnerview.SimplyDone.dto.JobMapper;
import com.learnerview.SimplyDone.dto.JobResponse;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.DeadLetterJob;
import com.learnerview.SimplyDone.repository.JobRepository;
import com.learnerview.SimplyDone.service.AdminService;
import com.learnerview.SimplyDone.service.JobService;
import com.learnerview.SimplyDone.service.RateLimitingService;
import com.learnerview.SimplyDone.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of AdminService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final JobRepository jobRepository;
    private final JobService jobService;
    private final RetryService retryService;
    private final RateLimitingService rateLimitingService;

    @Override
    public Map<String, Object> getSystemStats() {
        long[] queueSizes = jobService.getQueueSizes();
        long executedJobs = jobRepository.getExecutedJobsCount();
        long rejectedJobs = jobRepository.getRejectedJobsCount();

        Map<String, Object> stats = new HashMap<>();
        stats.put("highQueueSize", queueSizes[0]);
        stats.put("lowQueueSize", queueSizes[1]);
        stats.put("totalQueueSize", queueSizes[0] + queueSizes[1]);
        stats.put("executedJobs", executedJobs);
        stats.put("rejectedJobs", rejectedJobs);
        stats.put("totalProcessed", executedJobs + rejectedJobs);
        stats.put("timestamp", Instant.now().toString());

        return stats;
    }

    @Override
    public List<JobResponse> getQueueJobs(JobPriority priority) {
        return jobRepository.getAllJobsInQueue(priority).stream()
                .map(JobMapper::toJobResponse)
                .toList();
    }

    @Override
    public Map<String, Object> getHealthInfo() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "SimplyDone");
        health.put("version", "1.0.0");
        health.put("description", "Enterprise Job Scheduling Library");
        health.put("timestamp", Instant.now().toString());

        Runtime runtime = Runtime.getRuntime();
        health.put("jvm", Map.of(
                "maxMemory", runtime.maxMemory(),
                "totalMemory", runtime.totalMemory(),
                "freeMemory", runtime.freeMemory(),
                "usedMemory", runtime.totalMemory() - runtime.freeMemory()
        ));

        long[] queueSizes = jobService.getQueueSizes();
        health.put("queues", Map.of(
                "highPrioritySize", queueSizes[0],
                "lowPrioritySize", queueSizes[1],
                "totalSize", queueSizes[0] + queueSizes[1]
        ));

        return health;
    }

    @Override
    public Map<String, Object> clearQueues() {
        long[] queueSizes = jobService.getQueueSizes();
        int clearedCount = jobRepository.clearAllQueues();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Queue clear operation completed");
        result.put("clearedJobs", Map.of(
                "highPriority", queueSizes[0],
                "lowPriority", queueSizes[1],
                "total", clearedCount
        ));
        return result;
    }

    @Override
    public Map<String, Object> clearQueue(JobPriority priority) {
        int clearedCount = jobRepository.clearQueue(priority);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", priority.name() + " priority queue cleared");
        result.put("clearedJobs", clearedCount);
        return result;
    }

    @Override
    public Map<String, Object> getJobsByUser(String userId) {
        List<Job> highPriorityJobs = jobRepository.getAllJobsInQueue(JobPriority.HIGH);
        List<Job> lowPriorityJobs = jobRepository.getAllJobsInQueue(JobPriority.LOW);

        List<JobResponse> userHighJobs = highPriorityJobs.stream()
                .filter(job -> userId != null && userId.equals(job.getUserId()))
                .map(JobMapper::toJobResponse)
                .toList();

        List<JobResponse> userLowJobs = lowPriorityJobs.stream()
                .filter(job -> userId != null && userId.equals(job.getUserId()))
                .map(JobMapper::toJobResponse)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("highPriorityJobs", userHighJobs);
        response.put("lowPriorityJobs", userLowJobs);
        response.put("totalJobs", userHighJobs.size() + userLowJobs.size());
        response.put("timestamp", Instant.now().toString());

        return response;
    }

    @Override
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        metrics.put("jvm", Map.of(
                "maxMemory", runtime.maxMemory(),
                "totalMemory", runtime.totalMemory(),
                "freeMemory", runtime.freeMemory(),
                "usedMemory", runtime.totalMemory() - runtime.freeMemory(),
                "availableProcessors", runtime.availableProcessors()
        ));

        long[] queueSizes = jobService.getQueueSizes();
        long executedJobs = jobRepository.getExecutedJobsCount();
        long rejectedJobs = jobRepository.getRejectedJobsCount();

        metrics.put("jobProcessing", Map.of(
                "highPriorityQueueSize", queueSizes[0],
                "lowPriorityQueueSize", queueSizes[1],
                "totalQueueSize", queueSizes[0] + queueSizes[1],
                "executedJobs", executedJobs,
                "rejectedJobs", rejectedJobs,
                "totalProcessed", executedJobs + rejectedJobs,
                "successRate", executedJobs + rejectedJobs > 0 ?
                        (double) executedJobs / (executedJobs + rejectedJobs) * 100 : 0.0
        ));

        RetryService.RetryStatistics retryStats = retryService.getRetryStatistics();
        metrics.put("retry", Map.of(
                "retryingJobs", retryStats.retryingJobs(),
                "totalRetryAttempts", retryStats.totalAttempts(),
                "maxRetryAttempts", retryStats.maxAttempts()
        ));

        metrics.put("timestamp", Instant.now().toString());
        return metrics;
    }

    @Override
    public RetryService.RetryStatistics getRetryStatistics() {
        return retryService.getRetryStatistics();
    }

    @Override
    public RateLimitStatus getUserRateLimitStatus(String userId) {
        return rateLimitingService.getRateLimitStatus(userId);
    }

    @Override
    public List<DeadLetterJob> getDeadLetterJobs() {
        return retryService.getDeadLetterJobs();
    }

    @Override
    public int clearDeadLetterQueue() {
        return retryService.clearDeadLetterQueue();
    }

    @Override
    public boolean retryDeadLetterJob(String jobId) {
        return retryService.retryDeadLetterJob(jobId);
    }
}
