package com.learnerview.simplydone.model;

public enum JobStatus {
    QUEUED,
    RUNNING,
    RETRY_SCHEDULED,
    SUCCESS,
    FAILED,
    DLQ
}
