package com.learnerview.simplydone.service;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Deficit Round-Robin scheduler for worker polling.
 * Idle lanes accumulate deficit so lower-priority jobs still make progress.
 */
@Service
@Profile("worker")
@Slf4j
public class SchedulerEngine {

    private final QueueRepository queueRepo;
    private final JobEntityRepository jobRepo;
    private final JobExecutorService executor;

    private final JobPriority[] priorities = JobPriority.values();
    private final int[] weights;
    private final int[] deficit;
    private final int totalWeight;
    private final int leaseTimeoutSeconds;
    private final String workerId;

    public SchedulerEngine(QueueRepository queueRepo, JobEntityRepository jobRepo,
                           JobExecutorService executor, SchedulerProperties props) {
        this.queueRepo = queueRepo;
        this.jobRepo = jobRepo;
        this.executor = executor;

        this.weights = new int[]{
                props.getScheduler().getWeights().getHigh(),
                props.getScheduler().getWeights().getNormal(),
                props.getScheduler().getWeights().getLow()
        };
        this.deficit = new int[priorities.length];
        this.totalWeight = weights[0] + weights[1] + weights[2];
        this.leaseTimeoutSeconds = props.getWorker().getLeaseTimeoutSeconds();
        this.workerId = (System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "worker")
            + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelayString = "${simplydone.scheduler.polling-interval-ms:1000}")
    public void poll() {
        for (int i = 0; i < priorities.length; i++) {
            deficit[i] += weights[i];
        }

        int bestIdx = -1;
        int bestDeficit = Integer.MIN_VALUE;

        for (int i = 0; i < priorities.length; i++) {
            if (deficit[i] > bestDeficit && queueRepo.queueSize(priorities[i]) > 0) {
                bestDeficit = deficit[i];
                bestIdx = i;
            }
        }

        if (bestIdx == -1) return;

        Optional<String> claimed = queueRepo.claimNextReady(priorities[bestIdx]);
        if (claimed.isEmpty()) return;

        deficit[bestIdx] -= totalWeight;

        String jobId = claimed.get();
        Instant now = Instant.now();
        String leaseToken = UUID.randomUUID().toString();
        Instant visibleUntil = now.plusSeconds(leaseTimeoutSeconds);
        int updated = jobRepo.claimForExecution(jobId, leaseToken, workerId, visibleUntil, now,
            JobStatus.QUEUED, JobStatus.RUNNING);
        if (updated != 1) return;

        jobRepo.findById(jobId).ifPresentOrElse(
                executor::execute,
                () -> log.warn("Claimed job {} not found in DB", jobId)
        );
    }
}
