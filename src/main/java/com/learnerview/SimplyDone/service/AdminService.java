package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.dto.JobResponse;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.model.DeadLetterJob;
import com.learnerview.SimplyDone.model.JobPriority;

import java.util.List;
import java.util.Map;

// service interface for admin operations - single point of entry for AdminController
public interface AdminService {

    // returns basic queue stats - how many jobs are waiting, how many have run, how many failed
    Map<String, Object> getSystemStats();

    // get all the pending jobs in either the high or low priority queue
    List<JobResponse> getQueueJobs(JobPriority priority);

    // checks if redis is up, jvm memory usage, overall service status
    Map<String, Object> getHealthInfo();

    // wipe all jobs from every queue
    Map<String, Object> clearQueues();

    // wipe jobs from one specific queue (high or low)
    Map<String, Object> clearQueue(JobPriority priority);

    // get all jobs that a specific user has submitted
    Map<String, Object> getJobsByUser(String userId);

    // jvm memory info and job processing stats (success rate, total executed, etc.)
    Map<String, Object> getPerformanceMetrics();

    // retry statistics (retrying count, total attempts, max allowed)
    RetryService.RetryStatistics getRetryStatistics();

    // rate limit status for a specific user
    RateLimitStatus getUserRateLimitStatus(String userId);

    // dead letter queue operations
    List<DeadLetterJob> getDeadLetterJobs();
    int clearDeadLetterQueue();
    boolean retryDeadLetterJob(String jobId);

    // cancel a single job from a specific queue
    boolean cancelJob(String jobId, JobPriority priority);
}
