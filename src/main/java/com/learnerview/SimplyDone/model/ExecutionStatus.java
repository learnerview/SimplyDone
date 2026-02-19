package com.learnerview.SimplyDone.model;

/**
 * Enumeration representing the execution status of a job attempt.
 *
 * Execution Status Lifecycle:
 * STARTED -> COMPLETED or FAILED or TIMEOUT
 */
public enum ExecutionStatus {
    /**
     * Job execution has started
     */
    STARTED,

    /**
     * Job execution completed successfully
     */
    COMPLETED,

    /**
     * Job execution failed
     */
    FAILED,

    /**
     * Job execution timed out
     */
    TIMEOUT
}
