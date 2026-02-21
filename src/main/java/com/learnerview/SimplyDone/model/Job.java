package com.learnerview.SimplyDone.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// represents a single job in the system
// jobs are stored in redis sorted sets with the executeAt timestamp as the score
// that way we can efficiently grab jobs that are due to run
//
// validation belongs on DTOs, not on the domain model (Rule 14)
// serialization config belongs in Jackson configuration, not here
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    // auto-generated uuid so every job has a unique id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    // what kind of job this is - tells the executor which strategy class to use
    private JobType jobType;

    // description or content of the task
    private String message;

    // HIGH or LOW - affects which queue the job goes into
    private JobPriority priority;

    // how many seconds to wait before running the job (0 means run immediately)
    private int delaySeconds;

    // when the job was submitted (auto-set to now)
    @Builder.Default
    private Instant submittedAt = Instant.now();

    // when the job should run - this is the redis sorted set score
    private Instant executeAt;

    // who submitted this job - also used for rate limiting
    private String userId;

    // extra data the job needs - e.g. email address, file path, api endpoint
    private Map<String, Object> parameters;

    // how long the job is allowed to run before it's killed (default 5 min)
    @Builder.Default
    private int timeoutSeconds = 300;

    // max times to retry if the job fails (null = system default)
    private Integer maxRetries;

    // other job ids that must finish before this one starts
    private String[] dependencies;

    // when the job actually ran (null if it hasn't run yet)
    private Instant executedAt;

    // current state of the job - PENDING, EXECUTED, or FAILED
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    // how many times we've tried running this job (starts at 0)
    @Builder.Default
    private int attemptCount = 0;

    // whatever the job returned when it ran successfully
    private Object executionResult;

    // error details if the job failed
    private String errorMessage;

    // returns true if the job's scheduled run time has already passed
    @JsonIgnore
    public boolean isReadyForExecution() {
        return Instant.now().isAfter(executeAt) || Instant.now().equals(executeAt);
    }

    // sets status to EXECUTED and stamps the executedAt time
    public void markAsExecuted() {
        this.executedAt = Instant.now();
        this.status = JobStatus.EXECUTED;
    }

    // marks the job as failed
    public void markAsFailed() {
        this.executedAt = Instant.now();
        this.status = JobStatus.FAILED;
    }
}
