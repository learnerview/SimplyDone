package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.service.strategy.JobExecutionStrategy;

/**
 * Service responsible for the actual execution of a job's task.
 */
public interface JobExecutor {
    /**
     * Executes the task associated with the given job.
     * @param job The job to execute
     * @throws Exception if execution fails
     */
    void execute(Job job) throws Exception;
    
    /**
     * Executes the job asynchronously.
     * @param job The job to execute
     */
    void executeAsync(Job job);
    
    /**
     * Checks if the job can be executed (has a valid strategy).
     * @param job The job to check
     * @return true if the job can be executed, false otherwise
     */
    boolean canExecute(Job job);
    
    /**
     * Estimates the execution time for the job.
     * @param job The job to estimate
     * @return Estimated execution time in seconds, or -1 if cannot be executed
     */
    long estimateExecutionTime(Job job);
    
    /**
     * Gets the resource requirements for the job.
     * @param job The job to get requirements for
     * @return Resource requirements
     */
    JobExecutionStrategy.ResourceRequirements getResourceRequirements(Job job);
}
