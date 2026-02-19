package com.learnerview.SimplyDone.model;

/**
 * Enumeration representing types of user activities that are tracked.
 */
public enum ActivityType {
    /**
     * User submitted a new job
     */
    JOB_SUBMISSION,

    /**
     * User canceled a job
     */
    JOB_CANCELLATION,

    /**
     * Job was executed
     */
    JOB_EXECUTION,

    /**
     * User exceeded rate limit
     */
    RATE_LIMIT_EXCEEDED,

    /**
     * User authentication activity
     */
    AUTHENTICATION,

    /**
     * Admin access or operation
     */
    ADMIN_ACCESS
}
