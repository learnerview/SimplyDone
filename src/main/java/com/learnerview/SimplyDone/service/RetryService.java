package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.model.DeadLetterJob;
import com.learnerview.SimplyDone.model.Job;

import java.util.List;

// handles retrying failed jobs and the dead letter queue
public interface RetryService {
    boolean retryJob(Job job, Exception error);
    void resetRetryAttempts(String jobId);
    RetryStatistics getRetryStatistics();
    List<DeadLetterJob> getDeadLetterJobs();
    int clearDeadLetterQueue();
    boolean retryDeadLetterJob(String deadLetterJobId);

    // holds retry stats - jobs currently in retry state, total attempts made, max allowed
    record RetryStatistics(
            int retryingJobs,
            int totalAttempts,
            int maxAttempts
    ) {}
}
