package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.model.JobPriority;

import java.util.Optional;

public interface QueueRepository {

    void enqueue(String jobId, JobPriority priority, long scheduledAtEpochMs);

    Optional<String> claimNextReady(JobPriority priority);

    void remove(String jobId, JobPriority priority);

    long queueSize(JobPriority priority);

    void clearQueue(JobPriority priority);

    void clearAll();
}
