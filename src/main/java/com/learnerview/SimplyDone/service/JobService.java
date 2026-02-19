package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.dto.EnhancedJobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobSubmissionResponse;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.service.strategy.JobExecutionStrategy;

// service interface for all job operations
// JobServiceImpl is the class that actually does all this stuff
public interface JobService {
    JobSubmissionResponse submitJob(JobSubmissionRequest request);
    boolean executeNextReadyJob();
    com.learnerview.SimplyDone.model.Job getJobById(String jobId);
    boolean cancelJob(String jobId);
    long[] getQueueSizes();
    
    // enhanced submission with scheduling, retries, dependencies, etc.
    JobSubmissionResponse submitEnhancedJob(EnhancedJobSubmissionRequest request);

    // get the status of a batch of jobs by batch id
    Object getBatchStatus(String batchId);

    // cancel all jobs in a batch
    void cancelBatch(String batchId);

    // Enhanced methods for new features
    long estimateExecutionTime(Job job);  // rough estimate of how long a job will take
    JobExecutionStrategy.ResourceRequirements getResourceRequirements(Job job);  // memory/cpu requirements
    boolean canExecute(Job job);  // check if the system has resources to run this job right now
}
