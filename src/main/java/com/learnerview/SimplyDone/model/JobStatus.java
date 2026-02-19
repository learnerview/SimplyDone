package com.learnerview.SimplyDone.model;

/**
 * Enumeration representing the status of a job in its lifecycle.
 *
 * Job Status Lifecycle:
 * PENDING -> EXECUTED (success) or FAILED (failure)
 */
public enum JobStatus {
    /**
     * Job is waiting to be executed
     */
    PENDING,

    /**
     * Job has been successfully executed
     */
    EXECUTED,

    /**
     * Job execution failed after all retry attempts
     */
    FAILED
}
